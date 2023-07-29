package com.hmdp.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * @author Administrator
 * @date 2023/7/23 20:01
 */
@Configuration
public class CaffeineConfig {
    @Bean
    public Cache<String, Object> cacheConfig() {
        return Caffeine.newBuilder()
                .initialCapacity(128)
                .maximumSize(1024)
                //在写操作之后，5分钟就将缓存清除
                .expireAfterWrite(5L, TimeUnit.MINUTES)
                .build();
    }
}
