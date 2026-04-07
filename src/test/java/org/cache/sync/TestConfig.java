package org.cache.sync;

import org.cache.sync.core.CacheCleanHandler;
import org.cache.sync.core.CacheSyncConsumer;
import org.cache.sync.core.CacheSyncPublisher;
import org.cache.sync.core.RedisStreamCacheSyncPublisher;
import org.cache.sync.config.CacheSyncProperties;
import org.cache.sync.metrics.CacheSyncMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.Map;

@Configuration
public class TestConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        // 连接到本地 Redis 实例
        return new LettuceConnectionFactory("127.0.0.1", 6379);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        return template;
    }

    @Bean
    public CacheSyncProperties cacheSyncProperties() {
        CacheSyncProperties properties = new CacheSyncProperties();
        properties.setEnabled(true);
        properties.setStreamKey("cache:sync:stream:test");
        properties.setConsumerGroupPrefix("Test");
        properties.setMessageTimeoutMs(5000);
        properties.setBatchSize(10);
        properties.setBlockMs(1000);
        properties.setMaxLen(100);
        properties.setThreadPoolSize(1);
        properties.setEnableMetrics(true);
        return properties;
    }

    @Bean
    public CacheSyncMetrics cacheSyncMetrics() {
        return new CacheSyncMetrics();
    }

    @Bean
    public CacheCleanHandler testCacheCleanHandler() {
        return new CacheCleanHandler() {
            @Override
            public String supportType() {
                return "test";
            }

            @Override
            public String supportSubType() {
	            return CacheCleanHandler.super.supportSubType();
            }

            @Override
            public void cacheSync(String type, String subType, String cacheKey, Map<String, String> metadata) {
                System.out.println("测试清理缓存: type=" + type + ", subType=" + subType + ", cacheKey=" + cacheKey);
            }
        };
    }

    @Bean
    public CacheSyncPublisher cacheSyncPublisher(RedisTemplate<String, Object> redisTemplate, 
                                                CacheSyncProperties properties, 
                                                CacheSyncMetrics metrics) {
        return new RedisStreamCacheSyncPublisher(redisTemplate, properties, metrics);
    }

    @Bean
    public CacheSyncConsumer cacheSyncConsumer(RedisTemplate<String, Object> redisTemplate, 
                                              CacheSyncProperties properties, 
                                              CacheSyncMetrics metrics) {
        return new CacheSyncConsumer(redisTemplate, properties, metrics);
    }

}
