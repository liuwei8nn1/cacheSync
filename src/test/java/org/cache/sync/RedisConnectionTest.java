package org.cache.sync;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

public class RedisConnectionTest {
    public static void main(String[] args) {
        try {
            // 连接到本地 Redis 实例
            LettuceConnectionFactory factory = new LettuceConnectionFactory("127.0.0.1", 6379);
            factory.afterPropertiesSet();
            
            RedisTemplate<String, String> template = new RedisTemplate<>();
            template.setConnectionFactory(factory);
            template.setKeySerializer(new StringRedisSerializer());
            template.setValueSerializer(new StringRedisSerializer());
            template.afterPropertiesSet();
            
            // 测试连接
            String value = template.opsForValue().get("test");
            System.out.println("Redis 连接成功！");
            System.out.println("测试值: " + value);
            
            // 测试写入
            template.opsForValue().set("test", "Hello Redis!");
            value = template.opsForValue().get("test");
            System.out.println("写入后的值: " + value);
            
            factory.destroy();
        } catch (Exception e) {
            System.err.println("Redis 连接失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
