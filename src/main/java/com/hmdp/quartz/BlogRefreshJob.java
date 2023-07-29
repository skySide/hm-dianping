package com.hmdp.quartz;

import cn.hutool.core.date.StopWatch;
import com.hmdp.entity.Blog;
import com.hmdp.service.IBlogService;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

/**
 * 博客刷新job
 * @author Administrator
 * @date 2023/7/23 15:59
 */
@Component
@Slf4j
public class BlogRefreshJob implements Job {
    
    @Resource(name = "stringRedisTemplate")
    private RedisTemplate stringRedisTemplate;

    @Resource
    private IBlogService blogService;
    
    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        BoundSetOperations operations = stringRedisTemplate.boundSetOps(RedisConstants.BLOG_REFRESH_KEY);
        if(operations.size() <= 0) {
            log.info("定时任务blog数量为0");
            return;
        }
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        log.info("quartz定时任务开始....");
        while(operations.size() > 0){
            refresh(Integer.parseInt((String)operations.pop()));
        }
        stopWatch.stop();
        log.info("quartz定时任务结束...,用时: {} s", stopWatch.getTotalTimeSeconds());
    }

    private void refresh(int blogId) {
        Blog blog = blogService.getById(blogId);
        if(Objects.isNull(blog)) {
            return;
        }
        Integer liked = blog.getLiked();
        Integer comments = blog.getComments();
        LocalDate createDate = blog.getCreateTime().toLocalDate();
        LocalDate nowDate = LocalDateTime.now().toLocalDate();
        //计算blog的权重,进而计算blog的得分
        Long weight = liked * 10L + comments;
        Double score = Math.log10(weight) + (nowDate.toEpochDay() - createDate.toEpochDay());
        //更新对应blog的得分
        blog.setScore(score);
        blogService.updateById(blog);
    }
}
