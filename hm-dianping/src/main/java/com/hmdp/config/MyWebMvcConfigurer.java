package com.hmdp.config;

import com.hmdp.interceptor.LoginInterceptor;
import com.hmdp.interceptor.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration //配置拦截器
public class MyWebMvcConfigurer implements WebMvcConfigurer {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override //添加拦截的路径
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate));
        registry.addInterceptor(new LoginInterceptor()) //添加自定义的拦截器
                .excludePathPatterns(
                        //对下面的资源不需要进行拦截
                        "/shop/**",
                        "/voucher/**",
                        "/blog/hot",
                        "/blog/{id}",
                        "/blog/likes/{id}",
                        "/follow/or/not/{followUserId}",
                        "/shop-type/**",
                        "/user/login",
                        "/user/code",
                        "/user/{userId}",
                        "**/index.html"
                );
    }
}
