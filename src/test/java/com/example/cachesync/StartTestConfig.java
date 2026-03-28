package com.example.cachesync;

import java.util.Map;

import com.example.cachesync.config.CacheSyncAutoConfiguration;
import com.example.cachesync.core.CacheCleanHandler;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@ImportAutoConfiguration({
        CacheSyncAutoConfiguration.class,
})
public class StartTestConfig {

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
    public CacheCleanHandler testCacheCleanHandler() {
        return new CacheCleanHandler() {
            @Override
            public String supportType() {
                return "test";
            }

            @Override
            public String supportSubType() {
                return "*";
            }

            @Override
            public void cacheSync(String type, String subType, String cacheKey, Map<String, String> metadata) {
                System.out.println("测试清理缓存: type=" + type + ", subType=" + subType + ", cacheKey=" + cacheKey);
            }
        };
    }

}  
