package org.cache.sync.core;

import java.util.HashMap;
import java.util.Map;

import org.cache.sync.config.CacheSyncProperties;
import org.cache.sync.metrics.CacheSyncMetrics;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class RedisStreamCacheSyncPublisher implements CacheSyncPublisher {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheSyncProperties properties;
    private final CacheSyncMetrics metrics;

    public RedisStreamCacheSyncPublisher(RedisTemplate<String, Object> redisTemplate, 
                                         CacheSyncProperties properties, 
                                         CacheSyncMetrics metrics) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    public void publishCacheClean(String type, String subType, String cacheKey,  Map<String, String> metadata, boolean afterTransaction) {
        if (!properties.isEnabled()) {
            return;
        }
        Map<String, String> message = new HashMap<>(metadata);
        message.put(Constants.CACHE_KEY, cacheKey);
        message.put(Constants.TYPE, type);
        message.put(Constants.SUB_TYPE, subType);
        message.put(Constants.TIMESTAMP, String.valueOf(System.currentTimeMillis()));

        try {
            if (afterTransaction) {
                boolean synchronizationActive = TransactionSynchronizationManager.isSynchronizationActive();
                if (synchronizationActive) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            cacheClean(properties.getStreamKey(), message);
                        }
                    });
                }else {
                    cacheClean(properties.getStreamKey(), message);
                }
            }else {
                cacheClean(properties.getStreamKey(), message);
            }

        } catch (Exception e) {
            metrics.incrementFailedMessages();
            throw new RuntimeException("Failed to publish cache clean message", e);
        }
    }

    private void cacheClean(String streamKey, Map<String, String> message){
        // 使用 RedisTemplate 发布消息
        // 注意：由于 Spring Data Redis 版本限制，这里直接使用 add 方法
        // 实际生产环境中，可能需要通过其他方式设置 MAXLEN
        redisTemplate.opsForStream().add(streamKey, message);
        // 异步修剪 Stream，保持最大长度
        trimStream(streamKey);
        metrics.incrementPublishedMessages();
    }

    /**
     * 修剪 Stream 到配置的最大长度
     * 使用近似修剪（~）以提高性能
     */
    private void trimStream(String streamKey) {
        try {
            redisTemplate.execute((RedisCallback<Void>) connection -> {
                byte[] keyBytes = redisTemplate.getStringSerializer().serialize(streamKey);
                if (keyBytes != null) {
                    // 使用 XTRIM 命令，APPROXIMATE 模式提高性能
                    connection.streamCommands().xTrim(
                            keyBytes,
                            properties.getMaxLen(),
                            true  // true 表示使用 APPROXIMATE 模式
                    );
                }
                return null;
            });
        } catch (Exception e) {
            // 修剪失败不影响主流程，记录日志即可
            // 可以考虑增加 metrics 统计
        }
    }

}
