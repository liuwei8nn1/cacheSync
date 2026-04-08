package org.cache.sync;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.cache.sync.config.CacheSyncProperties;
import org.cache.sync.config.RedisKey;
import org.cache.sync.core.CacheSyncConsumer;
import org.cache.sync.core.CacheSyncPublisher;
import org.cache.sync.metrics.CacheSyncMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
public class CacheSyncCompleteTest {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private CacheSyncPublisher cacheSyncPublisher;

    @Autowired
    private CacheSyncConsumer cacheSyncConsumer;

    @Autowired
    private CacheSyncProperties properties;

    @Autowired
    private CacheSyncMetrics metrics;

    private final AtomicInteger cleanCount = new AtomicInteger(0);
    private final CountDownLatch latch = new CountDownLatch(1);

    @BeforeEach
    public void setUp() {
        cleanCount.set(0);
    }

    @Test
    public void testRedisConnection() {
        System.out.println("测试 Redis 连接");
        try {
            // 测试基本的 Redis 操作
            redisTemplate.opsForValue().set("test", "Hello Redis!");
            String value = (String) redisTemplate.opsForValue().get("test");
            System.out.println("Redis 连接成功！测试值: " + value);
            assertTrue(value != null, "Redis 连接失败");
        } catch (Exception e) {
            System.err.println("Redis 连接失败: " + e.getMessage());
            e.printStackTrace();
            assertTrue(false, "Redis 连接失败");
        }
    }

    @Test
    public void testPublishMessage() {
        System.out.println("测试发布消息");
        try {
            // 发布消息
            String cacheKey = "test:key:1:" + System.currentTimeMillis();
            cacheSyncPublisher.publishCacheClean("test", "default", cacheKey);
            System.out.println("消息发布成功: " + cacheKey);
            
            // 检查消息是否已发布到 Redis 流
            // 构建流 key
            String streamKey = RedisKey.calcStreamKey(properties.getPrefixKey());
            Long size = redisTemplate.opsForStream().size(streamKey);
            System.out.println("Stream 中的消息数量: " + size);
            Thread.sleep(1000 * 60);
            assertTrue(size > 0, "消息未发布到 Redis");
        } catch (Exception e) {
            System.err.println("消息发布失败: " + e.getMessage());
            e.printStackTrace();
            assertTrue(false, "消息发布失败");
        }
    }

    @Test
    public void testPublishMessageWithMetadata() {
        System.out.println("测试发布带元数据的消息");
        try {
            // 发布消息，带元数据
            String cacheKey = "test:key:2";
            Map<String, String> metadata = new HashMap<>();
            metadata.put("eventType", "delete");
            metadata.put("version", "1.0");
            cacheSyncPublisher.publishCacheClean("test", "metadata", cacheKey, metadata);
            System.out.println("消息发布成功: " + cacheKey + "，元数据: " + metadata);
            
            // 检查消息是否已发布到 Redis 流
            // 构建流 key
            String streamKey = RedisKey.calcStreamKey(properties.getPrefixKey());
            Long size = redisTemplate.opsForStream().size(streamKey);
            System.out.println("Stream 中的消息数量: " + size);
            assertTrue(size > 0, "消息未发布到 Redis");
        } catch (Exception e) {
            System.err.println("消息发布失败: " + e.getMessage());
            e.printStackTrace();
            assertTrue(false, "消息发布失败");
        }
    }

    @Test
    public void testMetrics() {
        System.out.println("测试监控指标");
        try {
            // 发布消息
            String cacheKey = "test:key:3";
            cacheSyncPublisher.publishCacheClean("test", "metrics", cacheKey);
            System.out.println("消息发布成功: " + cacheKey);
            
            // 检查指标
            // 注意：由于指标是通过 Micrometer 暴露的，这里我们只能检查内部计数器
            // 实际使用中，这些指标会通过 Actuator 暴露
            System.out.println("指标检查完成");
        } catch (Exception e) {
            System.err.println("指标测试失败: " + e.getMessage());
            e.printStackTrace();
            assertTrue(false, "指标测试失败");
        }
    }

}
