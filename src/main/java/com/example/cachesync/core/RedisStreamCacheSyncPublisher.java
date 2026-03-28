package com.example.cachesync.core;

import java.util.HashMap;
import java.util.Map;

import com.example.cachesync.config.CacheSyncProperties;
import com.example.cachesync.metrics.CacheSyncMetrics;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Transactional;
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
        redisTemplate.opsForStream().add(
                streamKey,
                message
        );
        metrics.incrementPublishedMessages();
    }

}
