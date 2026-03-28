package com.example.cachesync.core;

import java.util.HashMap;
import java.util.Map;

import com.example.cachesync.config.CacheSyncProperties;
import com.example.cachesync.metrics.CacheSyncMetrics;
import org.springframework.data.redis.core.RedisTemplate;

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
    public void publishCacheClean(String type, String subType, String cacheKey) {
        publishCacheClean(type, subType, cacheKey, new HashMap<>());
    }

    @Override
    public void publishCacheClean(String type, String subType, String cacheKey,  Map<String, String> metadata) {
        if (!properties.isEnabled()) {
            return;
        }
        Map<String, String> message = new HashMap<>(metadata);
        message.put("cacheKey", cacheKey);
        message.put("type", type);
        message.put("subType", subType);
        message.put("timestamp", String.valueOf(System.currentTimeMillis()));

        try {
            // 使用 RedisTemplate 发布消息
            redisTemplate.opsForStream().add(
                    properties.getStreamKey(),
                    message
            );
            metrics.incrementPublishedMessages();
        } catch (Exception e) {
            metrics.incrementFailedMessages();
            throw new RuntimeException("Failed to publish cache clean message", e);
        }
    }

}
