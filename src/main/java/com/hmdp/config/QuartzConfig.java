package com.hmdp.config;

import com.hmdp.quartz.BlogRefreshJob;
import com.hmdp.utils.QuartzConstants;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;
import org.springframework.scheduling.quartz.SimpleTriggerFactoryBean;

/**
 * quartz定时任务配置管理
 * @author Administrator
 * @date 2023/7/23 16:01
 */
@Configuration
public class QuartzConfig {

    @Bean
    public JobDetailFactoryBean blogRefreshJobDetail() {
        JobDetailFactoryBean factoryBean = new JobDetailFactoryBean();
        //JobDetail是一个Job的任务实例，所以通过setJobClass来执行这个jobDetail执行的是哪个Job
        factoryBean.setJobClass(BlogRefreshJob.class);
        //设置JobDetail名字以及组名
        factoryBean.setName(QuartzConstants.BLOG_REFRESH_JOB_DETAIL);
        factoryBean.setGroup(QuartzConstants.BLOG_REFRESH_JOB_GROUP_NAME);
        //是否持久化到数据库中
        factoryBean.setDurability(true);
        //任务是否可以恢复
        factoryBean.setRequestsRecovery(true);
        return factoryBean;
    }

    @Bean
    public SimpleTriggerFactoryBean simpleTriggerFactoryBean(JobDetail blogRefreshJobDetail) {
        SimpleTriggerFactoryBean simpleTriggerFactoryBean = new SimpleTriggerFactoryBean();
        //设置这个trigger绑定的是哪一个jobDetail
        simpleTriggerFactoryBean.setJobDetail(blogRefreshJobDetail);
        //设置trigger的名字以及组名
        simpleTriggerFactoryBean.setName(QuartzConstants.BLOG_REFRESH_JOB_TRIGGER);
        simpleTriggerFactoryBean.setGroup(QuartzConstants.BLOG_REFRESH_JOB_TRIGGER_GROUP_NAME);
        //设置每隔5分钟就进行一次刷新
        simpleTriggerFactoryBean.setRepeatInterval(QuartzConstants.BLOG_REFRESH_JOB_REPEAT_INTERVAL);
        //设置存储数据的类型
        simpleTriggerFactoryBean.setJobDataMap(new JobDataMap());
        return simpleTriggerFactoryBean;
    }
}
