package com.hmdp.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "hm_dianping:login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_TOKEN_KEY = "hm_dianping:login:token:";
    public static final Long LOGIN_TOKEN_TTL = 30L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "hm_dianping:cache:shop:";

    public static final String LOCK_SHOP_KEY = "hm_dianping:lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String STREAM_ORDER_KEY = "hm_dianping:stream:orders";
    public static final String SECKILL_STOCK_KEY = "hm_dianping:seckill:voucher:stock:";
    public static final String BLOG_LIKED_KEY = "hm_dianping:blog:liked:";
    public static final String FEED_KEY = "hm_dianping:feed:";
    public static final String SHOP_GEO_KEY = "hm_dianping:shop:geo:";
    public static final String USER_SIGN_KEY = "hm_dianping:sign:";

    public static final String SHOP_TYPE_LIST = "hm_dianping:shop:type-list";

    public static final String FOLLOW_USER_KEY = "hm_dianping:follow:user:";
}
