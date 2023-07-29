package com.hmdp.utils;

import com.hmdp.service.IShopTypeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author Administrator
 * @date 2023/7/23 22:50
 */
@Slf4j
public class RedisBloomFilter {

    private StringRedisTemplate stringRedisTemplate;

    private BloomFilterHelper bloomFilterHelper;

    public RedisBloomFilter(StringRedisTemplate stringRedisTemplate, BloomFilterHelper bloomFilterHelper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.bloomFilterHelper = bloomFilterHelper;
    }

    public void add(String key) {
        int[] bits = bloomFilterHelper.murmurHashOffset(key);
        for(int bit : bits) {
           stringRedisTemplate.opsForValue().setBit(RedisConstants.REDIS_BLOOM_FILTER_KEY, bit, true);
        }
    }

    public boolean containsKey(String key) {
        int[] offsets = bloomFilterHelper.murmurHashOffset(key);
        boolean isContain = true;
        for(int offset : offsets) {
            isContain &= stringRedisTemplate.opsForValue().getBit(RedisConstants.REDIS_BLOOM_FILTER_KEY, offset);
            if(!isContain) {
                break;
            }
        }
        return isContain;
    }
}
