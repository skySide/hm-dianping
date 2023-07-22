package com.hmdp.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.exception.BizException;
import com.hmdp.service.IUserService;
import com.hmdp.strategy.LoginStrategy;
import com.hmdp.strategy.helper.LoginStrategyHelper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Objects;

/**
 * @author Administrator
 * @date 2023/7/22 17:30
 */
@Component
public class PasswordLoginStrategy implements LoginStrategy {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result login(LoginFormDTO loginFormDTO) throws BizException {
        String phone = loginFormDTO.getPhone();
        String password = loginFormDTO.getPassword();
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getPhone, phone);
        User user = userService.getOne(queryWrapper);
        if(Objects.isNull(user)) {
            user = userService.createUserWithPhoneAndPassword(phone, password);
        } else {
            if(!Objects.equals(password, user.getPassword())) {
                return Result.fail("密码错误");
            }
        }
        return LoginStrategyHelper.loginSuccess(stringRedisTemplate, user);
    }
}
