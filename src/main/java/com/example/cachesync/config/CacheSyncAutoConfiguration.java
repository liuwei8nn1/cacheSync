package com.example.cachesync.config;

import com.example.cachesync.annotation.LocalCacheEvictAspect;
import com.example.cachesync.core.*;
import com.example.cachesync.metrics.CacheSyncMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Configuration
@EnableConfigurationProperties(CacheSyncProperties.class)
@ConditionalOnProperty(prefix = "cache.sync", name = "enabled", havingValue = "true")
public class CacheSyncAutoConfiguration {

    private CacheSyncConsumer cacheSyncConsumer;

    @Bean
    public CacheSyncMetrics cacheSyncMetrics() {
        return new CacheSyncMetrics();
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
        cacheSyncConsumer = new CacheSyncConsumer(redisTemplate, properties, metrics);
        return cacheSyncConsumer;
    }

    @Bean
    public LocalCacheEvictAspect localCacheEvictAspect(CacheSyncPublisher cacheSyncPublisher) {
        return new LocalCacheEvictAspect(cacheSyncPublisher);
    }

    @Autowired(required = false)
    public void registerMetrics(MeterRegistry meterRegistry, CacheSyncMetrics metrics) {
        if (meterRegistry != null) {
            metrics.bindTo(meterRegistry);
        }
    }

    @PostConstruct
    public void startConsumer() {
        if (cacheSyncConsumer != null) {
            cacheSyncConsumer.start();
        }
    }

    @PreDestroy
    public void stopConsumer() {
        if (cacheSyncConsumer != null) {
            cacheSyncConsumer.stop();
        }
    }

}
