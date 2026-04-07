# 基于 Redis Streams 的多实例本地缓存同步工具

## 1. 项目简介

本项目是一个基于 Redis Streams 的轻量级 Spring Boot Starter，用于实现多实例部署场景下的本地缓存同步。当某个实例更新或删除数据时，会通知其他实例清理本地缓存，以保证缓存一致性。

### 核心特性

- **可靠广播**：每个实例都能收到缓存清理通知，且消息不丢失（至少一次）。
- **自动容错**：支持实例宕机后，未处理消息可被其他实例认领。
- **易于集成**：提供注解和 API，业务代码只需简单配置即可接入。
- **低侵入**：与现有本地缓存（如 Caffeine）解耦，仅提供清理事件机制。
- **监控指标**：暴露 Micrometer 指标，便于监控系统运行状态。
- **类型匹配**：支持基于 type 和 subType 的缓存清理处理器匹配机制，实现精细化缓存管理。
- **事务支持**：支持在事务提交后发送缓存清理消息，确保数据一致性。

## 2. 技术实现

- **消息存储**：使用 Redis 5.0+ 引入的 Streams 数据结构。
- **广播机制**：每个实例创建独立的消费者组，确保每个实例都能消费所有消息。
- **故障恢复**：定期扫描 Pending 消息，认领超时消息。
- **Stream 清理**：通过配置限制 Stream 长度，避免内存溢出。

## 3. 快速开始

### 3.1 依赖引入

在 Maven 项目的 `pom.xml` 文件中添加以下依赖：

```xml
<dependency>
    <groupId>org.cache.sync</groupId>
    <artifactId>cache-sync-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 3.2 配置项

在 `application.yml` 或 `application.properties` 文件中添加以下配置：

```yaml
cache:
  sync:
    enabled: true                        # 是否启用缓存同步组件
    stream-key: "cache:sync:stream"      # Redis Stream 的 key
    consumer-group-prefix: "cache-sync-group"  # 消费者组前缀，实际名称为 prefix-instanceId
    instance-id: ${spring.application.name}-${random.value}  # 实例唯一标识，默认随机生成
    message-timeout-ms: 300000           # 消息超时时间（毫秒），超过后可由其他实例认领，默认 5 分钟
    max-retry: 3                         # 最大重试次数（认领次数），超过则丢弃
    batch-size: 10                       # 每次 XREADGROUP 读取的消息数量
    block-ms: 5000                       # 阻塞读取时间（毫秒）
    max-len: 100                        # Stream 最大保留消息数量（通过 ~ 近似裁剪）
    thread-pool-size: 1                  # 消费线程数（通常 1 即可）
    enable-metrics: true                 # 是否暴露 Micrometer 指标
```

### 3.3 使用方式

#### 3.3.1 注解方式（推荐）

使用 `@LocalCacheEvict` 注解标记需要自动发布缓存清理消息的方法：

```java
import org.cache.sync.annotation.LocalCacheEvict;

@Service
public class MyService {

	// 基本用法，使用默认的type和subType
	@LocalCacheEvict(cacheKey = "#id")  // 当方法执行完后自动发布清理消息
	public void updateData(String id) {
		// 更新数据库逻辑
	}

	// 高级用法，指定type和subType
	@LocalCacheEvict(type = "user", subType = "profile", cacheKey = "#id")
	public void updateUserProfile(String id) {
		// 更新用户资料
	}

	// 非事务模式
	@LocalCacheEvict(type = "product", subType = "info", cacheKey = "#productId", afterTransaction = false)
	public void updateProductInfo(String productId) {
		// 更新产品信息
	}

}
```

#### 3.3.2 编程方式

直接注入 `CacheSyncPublisher` Bean 并调用其方法：

```java
import org.cache.sync.core.CacheSyncPublisher;

import java.util.HashMap;
import java.util.Map;

@Service
public class UserService {

	@Autowired
	private CacheSyncPublisher cacheSyncPublisher;

	public void deleteUser(String userId) {
		// 删除数据库
		userDao.delete(userId);

		// 构建元数据
		Map<String, String> metadata = new HashMap<>();
		metadata.put("operation", "delete");

		// 广播清理本地缓存
		cacheSyncPublisher.publishCacheClean("user", "profile", "user:" + userId, metadata, true);
	}

}
```

#### 3.3.3 自定义清理处理器

实现 `CacheCleanHandler` 接口来自定义缓存清理逻辑：

```java
import org.cache.sync.core.CacheCleanHandler;

import java.util.Map;

@Component
public class UserCacheCleanHandler implements CacheCleanHandler {

	static final Cache<Long, Object> localCache = Caffeine.newBuilder()
			.initialCapacity(4)
			.maximumSize(512)
			.expireAfterAccess(10, TimeUnit.MINUTES)
			.build();
	
	// 只处理用户相关的缓存清理
	@Override
	public String supportType() {
		return "user";
	}

	@Override
	public String supportSubType() {
		return "profile";
	}

	@Override
	public void cacheSync(String type, String subType, String cacheKey, Map<String, String> metadata) {
		// 清理本地缓存的实现
        // localCache.invalidateAll();
		localCache.invalidate(cacheKey);
		System.out.println("清理缓存: " + cacheKey + ", 操作: " + metadata.get("operation"));
	}

}
```

## 4. 监控指标

本组件暴露以下 Micrometer 指标：

- `cache.sync.messages.published`：发布消息总数（Counter）
- `cache.sync.messages.consumed`：消费成功总数（Counter）
- `cache.sync.messages.failed`：消费失败总数（Counter）
- `cache.sync.pending.size`：当前消费者组 PEL 中的消息数量（Gauge）
- `cache.sync.lag`：最近一次消费的 ID 与 Stream 最新 ID 的差距（Gauge）

这些指标可以通过 Spring Boot Actuator 暴露，便于监控系统运行状态。

## 5. 故障恢复

当实例宕机后，未处理的消息会保存在 Redis Streams 的 PEL（Pending Entries List）中。其他实例会定期扫描 PEL，认领超时的消息并处理，确保消息不会丢失。

## 6. 性能与可靠性

- **性能**：单实例处理能力 ≥ 1000 条/秒（消息体小，仅做缓存 key 清理）。
- **延迟**：发布延迟 P99 ≤ 10ms（网络正常，Redis 单机）；广播时，所有实例消费延迟 P99 ≤ 50ms。
- **可靠性**：依赖 Redis 高可用（Sentinel / Cluster），组件本身支持自动重连和故障切换。

## 7. 环境要求

- **Redis**：版本 ≥ 5.0（支持 Streams）
- **Spring Boot**：3.x
- **Java**：17+

## 8. 常见问题

### 8.1 Redis 连接失败

- 检查 Redis 服务是否正常运行
- 检查网络连接是否畅通
- 检查配置的 Redis 地址和端口是否正确

### 8.2 消息未被消费

- 检查实例是否正常运行
- 检查消费者组是否正确创建
- 检查 Stream 中是否有消息

### 8.3 内存占用过高

- 调整 `maxlen` 参数，限制 Stream 长度
- 定期清理 Stream 中的历史消息

## 9. 测试验证

项目包含完整的单元测试，验证以下功能：

- Redis 连接正常
- 消息发布功能正常
- 带元数据的消息发布功能正常
- 监控指标功能正常

## 10. 版本历史

- **1.0.0**：初始版本，实现基于 Redis Streams 的多实例本地缓存同步功能。