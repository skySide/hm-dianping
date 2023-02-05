package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 刷新用户token的拦截器
 * 代码中主要存在2个拦截器：RefreshTokenInterceptor以及LoginInterceptor
 * 其中LoginInterceptor是需要用户登录才需要拦截的，而RefreshTokenInterceptor
 * 则是访问任何路径都需要拦截，因为访问任何路径，都需要进行刷新token的有效期
 * 所以拦截顺序应该是RefreshTokenInterceptor -> LoginInterceptor
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;
    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //设置响应报文格式
        response.setContentType("text/html;charset=utf-8");
        //1、获取token
        String token = request.getHeader("authorization");
        if(StringUtils.isBlank(token)){
            return true;//放行，如果需要登录之后才可以操作，那么需要来到LoginInterceptor这个拦截器中
        }
        String key = RedisConstants.LOGIN_TOKEN_KEY + token;
        //2、从redis中获取用户数据userMap,并将userMap转成实体类userDTO
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        if(userMap.isEmpty()){
            //之所以不需要判断userMap为null,是因为entries方法返回的必然不是null(可以看源码得知)
            return true;//放行，如果需要登录之后才可以操作，那么需要来到LoginInterceptor这个拦截器中
        }
        //第三个参数isIngoreError,表示是否忽略类型转换错误
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        //3、将userDTO保存到ThreadLocal中
        UserHolder.saveUser(userDTO);
        //4、刷新token的有效期
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_TOKEN_TTL, TimeUnit.MINUTES);
        //5、放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
