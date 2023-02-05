package com.hmdp.utils;

public class RabbitConstant {
    public static final String SECKILL_QUEUE = "seckill_queue";
    public static final String SECKILL_EXCHANGE = "seckill_exchange";
    public static final String SECKILL_DEAD_LETTER_EXCHANGE = "seckill_dead_letter_exchange";
    public static final String SECKILL_DEAD_LETTER_QUEUE = "seckill_dead_letter_queue";
    public static final String SECKILL_ROUTING_KEY = "seckill.order";
    public static final String SECKILL_DEAD_LETTER_ROUTING_KEY = "dead_routing_key";
    public static final Integer SECKILL_MESSAGE_TTL = 1800000;
}
