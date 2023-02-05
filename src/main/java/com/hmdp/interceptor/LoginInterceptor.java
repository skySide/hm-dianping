package com.hmdp.interceptor;

import com.hmdp.dto.Result;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        //设置响应编码
        response.setContentType("text/html;charset=utf-8");
        //判断用户是否已经登录了(直接从ThreadLocal中取即可)
        //因为RefreshTokenHolder中刷新token的时候，已经将user保存到了ThreadLocal中了
        if(UserHolder.getUser() == null){
            response.getWriter().println(Result.fail("没有登录"));
            return false;
        }
        return true;
    }
}
