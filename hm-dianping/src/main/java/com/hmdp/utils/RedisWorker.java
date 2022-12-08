package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 全局唯一生成器
 */
@Component
public class RedisWorker {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    //以2022-1-1 00:00:00为基准
    private static final Long BEGIN_TIMESTAMP = 1640995200L;
    private static final Long BITE_OFFSET = 32L;
    /**
     * 生成prefixKey的下一个id：
     * 1、获取时间戳
     * 2、获取计数器
     * 3、最后的id就是 时间戳<32 | 计数器
     * @param prefixKey
     * @return
     */
    public Long nextId(String prefixKey){
        //1、获取时间戳
        Long current_timestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        long timestamp = current_timestamp - BEGIN_TIMESTAMP;
        //2、获取计数
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long count = stringRedisTemplate.opsForValue().increment("irc:" + prefixKey + time);
        //3、生成真正的下一个id: 时间戳<<32 | count
        return timestamp << BITE_OFFSET | count;
    }
}
