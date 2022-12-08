package com.hmdp.utils;

import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@ToString
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
