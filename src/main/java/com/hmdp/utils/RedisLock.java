package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class RedisLock implements ILock {
    private StringRedisTemplate stringRedisTemplate;
    /**
     * key的前缀
     */
    private final String LOCK_KEY_PREFIX = "hm_dianping:lock:";
    /**
     * key的名字
     */
    private String name;
    /**
     * key的值的前缀
     * toString为true，就会将产生的随机字符串的'-'符删除
     */
    private String LOCK_VALUE_PREFIX = UUID.randomUUID().toString(true);
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static{
        UNLOCK_SCRIPT = new DefaultRedisScript();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }
    public RedisLock(String name, StringRedisTemplate stringRedisTemplate) {
       this.stringRedisTemplate = stringRedisTemplate;
       this.name = name;
    }

    @Override
    public boolean tryLock(long timeout) {
        String threadId = LOCK_VALUE_PREFIX + Thread.currentThread().getId();//线程标识
        Boolean isTrue = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_KEY_PREFIX + name, threadId, timeout, TimeUnit.SECONDS);
        //因为上面setIfAbsent方法的返回值可能是null，所以如果自动拆箱的时候，
        // 就会发生报错，所以需要利用equals方法
        return Boolean.TRUE.equals(isTrue);
    }

    /*@Override
    public void unlock() {
        //获取key对应的值，也即创建锁的线程id
        String cache_id = stringRedisTemplate.opsForValue().get(LOCK_KEY_PREFIX + name);
        //获取当前线程的id
        String currentThreadId = LOCK_VALUE_PREFIX + Thread.currentThread().getId();
        if(currentThreadId.equals(cache_id)){
            //当前的key就是当前的线程创建的，那么可以进行删除key操作
            stringRedisTemplate.delete(LOCK_KEY_PREFIX + name);
        }

    }*/

    @Override
    public void unlock() {
        //通过lua脚本来保证分布式锁释放的原子性
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(LOCK_KEY_PREFIX + name),
                LOCK_VALUE_PREFIX + Thread.currentThread().getId());
    }
}
