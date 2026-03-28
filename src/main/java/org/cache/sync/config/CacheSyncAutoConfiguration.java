package org.cache.sync.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.cache.sync.annotation.LocalCacheEvictAspect;
import org.cache.sync.core.*;
import org.cache.sync.metrics.CacheSyncMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableConfigurationProperties(CacheSyncProperties.class)
@ConditionalOnProperty(prefix = "cache.sync", name = "enabled", havingValue = "true")
public class CacheSyncAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisTemplate<String, Object> cacheSyncRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key 使用 String 序列化
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Value 使用 JSON 序列化
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        template.afterPropertiesSet();
        return template;
    }

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
        return new CacheSyncConsumer(redisTemplate, properties, metrics);
    }

    @Bean
    public LocalCacheEvictAspect localCacheEvictAspect(CacheSyncPublisher cacheSyncPublisher) {
        return new LocalCacheEvictAspect(cacheSyncPublisher);
    }

    @Autowired(required = false)
    public void registerMetrics(MeterRegistry meterRegistry, CacheSyncMetrics metrics, CacheSyncProperties properties) {
        if (meterRegistry != null && properties.isEnableMetrics()) {
            metrics.bindTo(meterRegistry);
        }
    }

}
