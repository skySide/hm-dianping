package com.hmdp.utils;

/**
 * @author Administrator
 * @date 2023/7/23 16:16
 */
public interface QuartzConstants {
    /**
     * 博客刷新的jobDetail
     */
    String BLOG_REFRESH_JOB_DETAIL = "blogRefreshJobDetail";

    /**
     * 博客刷新的jobDetail的组名
     */
    String BLOG_REFRESH_JOB_GROUP_NAME = "group1";

    /**
     * 博客刷新的trigger
     */
    String BLOG_REFRESH_JOB_TRIGGER = "blogRefreshJobTrigger";

    /**
     * 博客刷新的trigger的组名
     */
    String BLOG_REFRESH_JOB_TRIGGER_GROUP_NAME = "group1";

    /**
     * 博客刷新的间隔： 5分钟
     */
    Long BLOG_REFRESH_JOB_REPEAT_INTERVAL = 1000L * 60L * 5L;


}
