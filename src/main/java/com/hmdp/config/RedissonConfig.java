package com.hmdp.config;

import com.google.common.base.Charsets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnel;
import com.google.common.hash.Funnels;
import com.hmdp.utils.BloomFilterHelper;
import com.hmdp.utils.RedisBloomFilter;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import javax.sound.midi.Sequence;
import java.nio.charset.Charset;

/**
 * @author MACHENIKE
 * redisson
 */
@Configuration
public class RedissonConfig {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Bean
    public RedissonClient redissonClient(){
        Config config = new Config();
        config.useSingleServer().setAddress("redis://192.168.252.130:6379").setPassword("123321");
        return Redisson.create(config);
    }

    @Bean
    public RedisBloomFilter redisBloomFilter() {
        BloomFilterHelper bloomFilterHelper = new BloomFilterHelper(10000, 0.03);
        return new RedisBloomFilter(stringRedisTemplate, bloomFilterHelper);
    }
}
