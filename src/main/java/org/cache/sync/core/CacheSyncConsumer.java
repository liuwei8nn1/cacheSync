package org.cache.sync.core;

import org.cache.sync.config.CacheSyncProperties;
import org.cache.sync.metrics.CacheSyncMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.NonNull;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CacheSyncConsumer implements ApplicationContextAware, SmartInitializingSingleton, DisposableBean {

	private static final Logger logger = LoggerFactory.getLogger(CacheSyncConsumer.class);

	private final RedisTemplate<String, Object> redisTemplate;
	private final CacheSyncProperties properties;
	private final CacheSyncMetrics metrics;

	private final ExecutorService executorService;
	private final ScheduledExecutorService scheduledExecutorService;
	private final AtomicBoolean running = new AtomicBoolean(true);
	private String consumerGroup;
	private String consumerName;
	ApplicationContext applicationContext;

	// 存储所有Handler的映射: type -> subType -> List<Handler>
	private Map<String, Map<String, List<CacheCleanHandler>>> handlerMapping = new HashMap<>();
	// 存储所有Handler的列表
	private List<CacheCleanHandler> allHandlers = new ArrayList<>();

	public CacheSyncConsumer(RedisTemplate<String, Object> redisTemplate,
	                         CacheSyncProperties properties,
	                         CacheSyncMetrics metrics) {
		this.redisTemplate = redisTemplate;
		this.properties = properties;
		this.metrics = metrics;
		// 固定线程池，用于消息消费
		this.executorService = Executors.newFixedThreadPool(properties.getThreadPoolSize());
		// 单线程定时任务线程池，用于扫描Pending消息
		this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

	}

	public void start() {
		if (!properties.isEnabled()) {
			return;
		}
		if (!running.get()) {
			return;
		}

		// 创建消费者组
		createConsumerGroup();

		// 启动消费线程
		for (int i = 0; i < properties.getThreadPoolSize(); i++) {
			String consumerName = this.consumerName + "-" + i;
			executorService.submit(() -> consumeMessages(consumerName));
		}

		// 启动 Pending 消息扫描定时任务（每30秒执行一次）
		scheduledExecutorService.scheduleAtFixedRate(this::scanPendingMessages, 0, 30, TimeUnit.SECONDS);
	}

	public void stop() {
		if (!properties.isEnabled()) {
			return;
		}
		if (!running.get()) {
			return;
		}
		running.set(false);
		try {
			// 关闭定时任务线程池
			scheduledExecutorService.shutdown();
			scheduledExecutorService.awaitTermination(properties.getGracefulShutdownTimeoutMs(), TimeUnit.MILLISECONDS);

			// 关闭消息消费线程池
			executorService.shutdown();
			executorService.awaitTermination(properties.getGracefulShutdownTimeoutMs(), TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void createConsumerGroup() {
		try {
			// 确保 Stream 存在
			try {
				// 尝试添加一个空消息来创建 Stream（如果不存在）
				redisTemplate.opsForStream().add(properties.getStreamKey(), new HashMap<>());
			} catch (Exception e) {
				// Stream 可能已经存在，忽略异常
			}

			// 尝试创建消费者组
			try {
				redisTemplate.opsForStream().createGroup(
						properties.getStreamKey(),
						consumerGroup
				);
				logger.info("Created consumer group: {}", consumerGroup);
			} catch (Exception e) {
				// 消费者组已存在，忽略异常
				logger.debug("Consumer group already exists: {}", consumerGroup);
			}
		} catch (Exception e) {
			logger.error("Error creating consumer group: {}", consumerGroup, e);
		}
	}

	/**
	 * 消费消息的主方法
	 * 功能：从Redis Stream中读取新消息并处理
	 * <p>
	 * 工作原理：
	 * 1. 从Stream中读取消息（使用block机制，避免轮询）
	 * 2. 解析消息内容（type, subType, cacheKey, metadata）
	 * 3. 调用handleCacheClean处理缓存清理
	 * 4. 确认消息（ack），将消息从pending列表中移除
	 * 5. 更新监控指标
	 * <p>
	 * 适用场景：处理正常的、新到达的缓存清理消息
	 */
	private void consumeMessages(String consumerName) {
		while (running.get()) {
			try {
				// 使用 RedisTemplate 读取消息
				List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream().read(
						Consumer.from(consumerGroup, consumerName),
						StreamReadOptions.empty()
								.count(properties.getBatchSize())
								.block(Duration.ofMillis(properties.getBlockMs())),
						StreamOffset.create(properties.getStreamKey(), ReadOffset.lastConsumed())
				);

				if (messages != null && !messages.isEmpty()) {
					for (MapRecord<String, Object, Object> record : messages) {
						Map<Object, Object> messageData = record.getValue();
						InternalMessage msg = InternalMessage.of(record);
						String subType = msg.subType, type = msg.type, messageId = msg.messageId, cacheKey = msg.cacheKey;
						Integer retrySize = msg.retrySize;
						HashMap<String, String> metadata = msg.metadata;
						if (msg.isValid()) {
							try {
								// 如果重试次数过大，直接ack抛弃
								if (retrySize > properties.getMaxRetry()) {
									logger.warn("Message exceeded max retry size, discarding: type={}, subType={}, cacheKey={}, retrySize={}",
											type, subType, cacheKey, retrySize);
									redisTemplate.opsForStream().acknowledge(
											properties.getStreamKey(),
											consumerGroup,
											messageId
									);
									metrics.incrementDiscardedMessages();
									continue;
								}

								// 处理缓存清理
								handleCacheClean(msg);
								// 确认消息
								redisTemplate.opsForStream().acknowledge(properties.getStreamKey(), consumerGroup, messageId);

								metrics.incrementConsumedMessages();
							} catch (Exception e) {
								logger.error("Failed to handle cache clean message: type={}, subType={}, cacheKey={}", type, subType, cacheKey, e);
								metrics.incrementFailedMessages();

								// 直接ack旧消息
								redisTemplate.opsForStream().acknowledge(properties.getStreamKey(), consumerGroup, messageId);

								// 重新发送一条新消息，带上重试次数
								try {
									// 修改次数
									messageData.put(Constants.RETRY_SIZE, retrySize + 1);
									// 发送新消息
									redisTemplate.opsForStream().add(properties.getStreamKey(), messageData);
									logger.info("Resent message with increased retry size: type={}, subType={}, cacheKey={}, retrySize={}",
											type, subType, cacheKey, retrySize + 1);
								} catch (Exception ex) {
									logger.error("Failed to resend message: type={}, subType={}, cacheKey={}", type, subType, cacheKey, ex);
								}
							}
						} else {
							// 直接ack
							redisTemplate.opsForStream().acknowledge(properties.getStreamKey(), consumerGroup, messageId);
							logger.warn("Invalid message format: cacheKey={}, type={}, subType={}", cacheKey, type, subType);
						}
					}
				}
			} catch (Exception e) {
				logger.error("Error consuming messages", e);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
			}
		}
	}

	/**
	 * 扫描Pending消息的方法
	 * 功能：扫描并处理超时的Pending消息（可能是由于消费者崩溃等原因未处理的消息）
	 * <p>
	 * 工作原理：
	 * 1. 定期（每30秒）检查Stream中的Pending消息
	 * 2. 认领（claim）超时的消息（超过messageTimeoutMs的消息）
	 * 3. 解析消息内容并处理
	 * 4. 确认消息并更新监控指标
	 * <p>
	 * 适用场景：处理因消费者崩溃、网络中断等原因导致的未处理消息
	 */
	private void scanPendingMessages() {
		if (!running.get()) {
			return;
		}

		try {
			// 确保消费者组存在
			createConsumerGroup();

			// 使用 RedisTemplate 查看 Pending 消息
			org.springframework.data.redis.connection.stream.PendingMessagesSummary pendingSummary = redisTemplate.opsForStream().pending(
					properties.getStreamKey(),
					consumerGroup
			);
			if (pendingSummary != null) {
				long pendingCount = pendingSummary.getTotalPendingMessages();
				metrics.setPendingSize(pendingCount);

				// 更新 lag 指标
				updateLagMetrics();

				// 清理离线的消费者组
				if (properties.isAutoCleanOfflineConsumers()) {
					cleanOfflineConsumers();
				}

				if (pendingCount > 0) {
					// 尝试认领超时消息
					// 从选区创建新的临时文件 使用 RedisTemplate 的 claim 方法
					List<MapRecord<String, Object, Object>> claimedMessages =
							redisTemplate.opsForStream().claim(
									properties.getStreamKey(),
									consumerGroup,
									consumerName,
									java.time.Duration.ofMillis(properties.getMessageTimeoutMs()),
									new org.springframework.data.redis.connection.stream.RecordId[0]
							);

					for (MapRecord<String, Object, Object> record : claimedMessages) {
						Map<Object, Object> messageData = record.getValue();
						InternalMessage msg = InternalMessage.of(record);
						String subType = msg.subType, type = msg.type, messageId = msg.messageId, cacheKey = msg.cacheKey;
						Integer retrySize = msg.retrySize;
						HashMap<String, String> metadata = msg.metadata;
						if (msg.isValid()) {
							try {
								// 如果重试次数过大，直接ack抛弃
								if (retrySize > properties.getMaxRetry()) {
									logger.warn("Claimed message exceeded max retry size, discarding: type={}, subType={}, cacheKey={}, retrySize={}",
											type, subType, cacheKey, retrySize);
									redisTemplate.opsForStream().acknowledge(properties.getStreamKey(), consumerGroup, messageId);
									metrics.incrementDiscardedMessages();
									continue;
								}

								// 处理缓存清理
								handleCacheClean(msg);
								// 确认消息
								redisTemplate.opsForStream().acknowledge(properties.getStreamKey(), consumerGroup, messageId);
								metrics.incrementConsumedMessages();
							} catch (Exception e) {
								logger.error("Failed to handle claimed cache clean message: type={}, subType={}, cacheKey={}", type, subType, cacheKey, e);
								metrics.incrementFailedMessages();

								// 直接ack旧消息
								redisTemplate.opsForStream().acknowledge(properties.getStreamKey(), consumerGroup, messageId);

								// 重新发送一条新消息，带上重试次数
								try {
									// 修改次数
									messageData.put(Constants.RETRY_SIZE, retrySize + 1);
									// 发送新消息
									redisTemplate.opsForStream().add(properties.getStreamKey(), messageData);
									logger.info("Resent claimed message with increased retry size: type={}, subType={}, cacheKey={}, retrySize={}",
											msg.type, msg.subType, msg.cacheKey, msg.retrySize + 1);
								} catch (Exception ex) {
									logger.error("Failed to resend claimed message: type={}, subType={}, cacheKey={}", type, subType, cacheKey, ex);
								}
							}
						} else {
							// 直接ack
							redisTemplate.opsForStream().acknowledge(properties.getStreamKey(), consumerGroup, messageId);
							logger.warn("Invalid claimed message format: cacheKey={}, type={}, subType={}", cacheKey, type, subType);
						}
					}
				}
			}
		} catch (Exception e) {
			logger.error("Error scanning pending messages", e);
		}
	}

	/**
	 * 更新 lag 指标
	 * 通过 XINFO GROUPS 命令获取消费者组的 lag 信息
	 */
	private void updateLagMetrics() {
		try {
			// 使用 RedisCallback 直接访问底层连接，执行 XINFO GROUPS 命令
			redisTemplate.execute((RedisCallback<?>) connection -> {
				byte[] keyBytes = redisTemplate.getStringSerializer().serialize(properties.getStreamKey());
				if (keyBytes != null) {
					try {
						// 获取消费者组信息 - 返回 XInfoGroups 对象
						org.springframework.data.redis.connection.stream.StreamInfo.XInfoGroups groups = 
							connection.streamCommands().xInfoGroups(keyBytes);
						if (groups != null) {
							// 查找匹配的消费者组 - 使用迭代器
							java.util.Iterator<org.springframework.data.redis.connection.stream.StreamInfo.XInfoGroup> iterator = groups.iterator();
							while (iterator.hasNext()) {
								org.springframework.data.redis.connection.stream.StreamInfo.XInfoGroup group = iterator.next();
								// 尝试通过 toString 解析 group 信息
								String groupStr = group.toString();
								if (groupStr.contains("consumerGroup=" + consumerGroup)) {
									// 从字符串中提取 lag 值（简单方式）
									// 格式类似：XInfoGroup[consumerGroup=group,lag=10,...]
									int lagIndex = groupStr.indexOf("lag=");
									if (lagIndex != -1) {
										int endIndex = groupStr.indexOf(',', lagIndex);
										if (endIndex == -1) {
											endIndex = groupStr.indexOf(']', lagIndex);
										}
										if (endIndex != -1) {
											String lagStr = groupStr.substring(lagIndex + 4, endIndex);
											try {
												long lag = Long.parseLong(lagStr.trim());
												metrics.setLag(lag);
											} catch (NumberFormatException e) {
												logger.debug("Could not parse lag from: {}", lagStr);
											}
										}
									}
									break;
								}
							}
						}
					} catch (Exception e) {
						logger.debug("Failed to get stream group info for lag metrics: {}", e.getMessage());
					}
				}
				return null;
			});
		} catch (Exception e) {
			logger.debug("Error updating lag metrics: {}", e.getMessage());
		}
	}

	/**
	 * 清理离线的消费者组
	 * 通过 XINFO CONSUMERS 命令检查每个消费者的最后活跃时间，清理超时的消费者
	 */
	private void cleanOfflineConsumers() {
		try {
			redisTemplate.execute((RedisCallback<?>) connection -> {
				byte[] keyBytes = redisTemplate.getStringSerializer().serialize(properties.getStreamKey());
				if (keyBytes != null) {
					try {
						// 获取所有消费者信息 - 使用 XInfoConsumers 对象
						org.springframework.data.redis.connection.stream.StreamInfo.XInfoConsumers consumers = 
							connection.streamCommands().xInfoConsumers(keyBytes, consumerGroup);
						if (consumers != null) {
							long timeoutMillis = properties.getOfflineConsumerTimeoutMinutes() * 60 * 1000;
							// 遍历所有消费者 - 使用迭代器
							Iterator<StreamInfo.XInfoConsumer> iterator = consumers.iterator();
							while (iterator.hasNext()) {
								StreamInfo.XInfoConsumer consumer = iterator.next();
								String groupName = consumer.groupName();
								String consumerName = consumer.consumerName();
								Duration idleTime = consumer.idleTime();
								long idleMillis = idleTime.toMillis();
								// 如果消费者空闲时间超过阈值，则清理该消费者(是缓存应用，没必要保留)
								if (idleMillis > timeoutMillis) {
									logger.info("Cleaning offline consumer: {}, idle time: {} ms", consumerName, idleMillis);
									try {
										// 直接删除该消费者
										connection.streamCommands().xGroupDelConsumer(keyBytes, consumerGroup, consumerName);
										logger.info("Successfully deleted offline consumer: {}", consumerName);
									} catch (Exception e) {
										logger.error("Error deleting consumer: {}", consumerName, e);
									}
								}
							}
						}
					} catch (Exception e) {
						logger.debug("Failed to get consumer info for cleanup: {}", e.getMessage());
					}
				}
				return null;
			});
		} catch (Exception e) {
			logger.debug("Error cleaning offline consumers: {}", e.getMessage());
		}
	}

	/**
	 * 处理缓存清理，根据type和subType查找对应的Handler执行
	 */
	private void handleCacheClean(InternalMessage msg) {
		String type = msg.type, subType = msg.subType, cacheKey = msg.cacheKey;
		HashMap<String, String> metadata = msg.metadata;
		List<CacheCleanHandler> handlers = findHandlers(type, subType);

		if (handlers.isEmpty()) {
			logger.warn("No CacheCleanHandler found for type={}, subType={}, cacheKey={}", type, subType, cacheKey);
			return;
		}

		for (CacheCleanHandler handler : handlers) {
			try {
				handler.cacheSync(type, subType, cacheKey, metadata);
				logger.debug("Cache clean handled by {}: type={}, subType={}, cacheKey={}",
						handler.getClass().getSimpleName(), type, subType, cacheKey);
			} catch (Exception e) {
				logger.error("Handler {} failed to process cache clean: type={}, subType={}, cacheKey={}", handler.getClass().getSimpleName(), type, subType, cacheKey, e);
				throw e;
			}
		}
	}

	/**
	 * 根据type和subType查找匹配的Handler列表
	 * 匹配优先级：
	 * 1. 精确匹配 (type, subType)
	 * 2. type精确 + subType通配 (*)
	 * 3. type通配 (*) + subType精确
	 * 4. 全部通配 (*, *)
	 */
	private List<CacheCleanHandler> findHandlers(String type, String subType) {
		List<CacheCleanHandler> result = new ArrayList<>();

		// 1. 精确匹配 (type, subType)
		Map<String, List<CacheCleanHandler>> subTypeMap = handlerMapping.get(type);
		if (subTypeMap != null) {
			List<CacheCleanHandler> exactHandlers = subTypeMap.get(subType);
			if (exactHandlers != null && !exactHandlers.isEmpty()) {
				result.addAll(exactHandlers);
			}

			// 2. type精确 + subType通配 (*)
			List<CacheCleanHandler> wildcardSubTypeHandlers = subTypeMap.get("*");
			if (wildcardSubTypeHandlers != null && !wildcardSubTypeHandlers.isEmpty()) {
				for (CacheCleanHandler handler : wildcardSubTypeHandlers) {
					if (!result.contains(handler)) {
						result.add(handler);
					}
				}
			}
		}

		// 3. type通配 (*) + subType精确
		Map<String, List<CacheCleanHandler>> wildcardTypeMap = handlerMapping.get("*");
		if (wildcardTypeMap != null) {
			List<CacheCleanHandler> wildcardTypeExactSubHandlers = wildcardTypeMap.get(subType);
			if (wildcardTypeExactSubHandlers != null && !wildcardTypeExactSubHandlers.isEmpty()) {
				for (CacheCleanHandler handler : wildcardTypeExactSubHandlers) {
					if (!result.contains(handler)) {
						result.add(handler);
					}
				}
			}

			// 4. 全部通配 (*, *)
			List<CacheCleanHandler> allWildcardHandlers = wildcardTypeMap.get("*");
			if (allWildcardHandlers != null && !allWildcardHandlers.isEmpty()) {
				for (CacheCleanHandler handler : allWildcardHandlers) {
					if (!result.contains(handler)) {
						result.add(handler);
					}
				}
			}
		}

		return result;
	}

	@Override
	public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	@Override
	public void afterSingletonsInstantiated() {
		this.consumerGroup = properties.getConsumerGroup();
		this.consumerName = properties.getInstanceId();
		// 把所有实现了 CacheCleanHandler 接口的bean找出来
		Map<String, CacheCleanHandler> handlerBeans = applicationContext.getBeansOfType(CacheCleanHandler.class);

		if (handlerBeans.isEmpty()) {
			logger.warn("No CacheCleanHandler implementation found! Cache sync messages will not be processed.");
			return;
		}

		logger.info("Found {} CacheCleanHandler implementations", handlerBeans.size());

		// 构建映射表
		for (Map.Entry<String, CacheCleanHandler> entry : handlerBeans.entrySet()) {
			CacheCleanHandler handler = entry.getValue();
			String beanName = entry.getKey();

			String supportType = handler.supportType();
			String supportSubType = handler.supportSubType();

			// 处理null值，默认使用*
			if (supportType == null) {
				supportType = "*";
			}
			if (supportSubType == null) {
				supportSubType = "*";
			}

			// 添加到映射表
			handlerMapping
					.computeIfAbsent(supportType, k -> new HashMap<>())
					.computeIfAbsent(supportSubType, k -> new ArrayList<>())
					.add(handler);

			allHandlers.add(handler);

			logger.info("Registered CacheCleanHandler: beanName={}, supportType={}, supportSubType={}",
					beanName, supportType, supportSubType);
		}

		logger.info("CacheCleanHandler mapping initialized with {} handlers", allHandlers.size());

		// 启动消费者
		start();
	}

	@Override
	public void destroy() {
		// 停止消费者
		stop();
	}

	public static class InternalMessage {

		public String messageId;
		public String type;
		public String subType;
		public String cacheKey;
		public Integer retrySize;
		public Long timestamp;
		public HashMap<String, String> metadata;

		public InternalMessage() {
		}

		boolean isValid() {
			return cacheKey != null && type != null && subType != null;
		}

		static InternalMessage of(MapRecord<String, Object, Object> record) {
			InternalMessage internalMessage = new InternalMessage();
			internalMessage.messageId = record.getId().getValue();
			Map<Object, Object> messageData = record.getValue();
			// 解析消息
			internalMessage.metadata = new HashMap<>();
			int retrySize = 0;
			for (Map.Entry<Object, Object> dataEntry : messageData.entrySet()) {
				String key = String.valueOf(dataEntry.getKey());
				String value = String.valueOf(dataEntry.getValue());
				if (Constants.CACHE_KEY.equals(key)) {
					internalMessage.cacheKey = value;
				} else if (Constants.TYPE.equals(key)) {
					internalMessage.type = value;
				} else if (Constants.SUB_TYPE.equals(key)) {
					internalMessage.subType = value;
				} else if (Constants.TIMESTAMP.equals(key)) {
					internalMessage.timestamp = Long.parseLong(value);
				} else if (Constants.RETRY_SIZE.equals(key)) {
					retrySize = Integer.parseInt(value);
				} else {
					internalMessage.metadata.put(key, value);
				}
			}
			internalMessage.retrySize = retrySize;
			return internalMessage;
		}

	}

}
