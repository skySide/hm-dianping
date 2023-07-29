package com.hmdp.utils;

public interface RedisConstants {
    String LOGIN_CODE_KEY = "hm_dianping:login:code:";
     Long LOGIN_CODE_TTL = 2L;
     String LOGIN_TOKEN_KEY = "hm_dianping:login:token:";
     Long LOGIN_TOKEN_TTL = 30L;

     Long CACHE_NULL_TTL = 2L;

     Long CACHE_SHOP_TTL = 30L;
     String CACHE_SHOP_KEY = "hm_dianping:cache:shop:";

     String LOCK_SHOP_KEY = "hm_dianping:lock:shop:";
     Long LOCK_SHOP_TTL = 10L;

     String STREAM_ORDER_KEY = "hm_dianping:stream:orders";
     String SECKILL_STOCK_KEY = "hm_dianping:seckill:voucher:stock:";
     String BLOG_LIKED_KEY = "hm_dianping:blog:liked:";
     String FEED_KEY = "hm_dianping:feed:";
     String SHOP_GEO_KEY = "hm_dianping:shop:geo:";
     String USER_SIGN_KEY = "hm_dianping:sign:";

     String SHOP_TYPE_LIST = "hm_dianping:shop:type-list";

     String FOLLOW_USER_KEY = "hm_dianping:follow:user:";
     String HOT_BLOG = "hm_dianping:hot-blog:";
    
     String BLOG_REFRESH_KEY = "hm_dianping:blog-refresh";

     String REDIS_BLOOM_FILTER_KEY = "hm_dianping:bloom_filter";
}
