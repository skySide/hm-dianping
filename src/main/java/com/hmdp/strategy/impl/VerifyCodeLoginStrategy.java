package com.hmdp.strategy.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import com.hmdp.strategy.LoginStrategy;
import com.hmdp.strategy.helper.LoginStrategyHelper;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author Administrator
 * @date 2023/7/22 16:53
 */
@Component
public class VerifyCodeLoginStrategy implements LoginStrategy {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result login(LoginFormDTO loginFormDTO) {
        // 1、校验手机号码的格式是否正确，这样是避免在发送一次验证码之后，修改了手机号的情况
        String phone = loginFormDTO.getPhone();
        String code = loginFormDTO.getCode();
        if(!RegexUtils.isPhoneValid(phone)){
            return Result.fail("手机号码格式错误");
        }
        //2、判断验证码是否正确
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        if(!Objects.equals(cacheCode, code)){
            //不正确，那么给出提示信息，表示验证码错误
            return Result.fail("验证码错误");
        }
        //3、获取这个用户，如果找不到，那么就注册
        User user = userService.getOne(new LambdaQueryWrapper<User>().eq(!Objects.isNull(phone), User::getPhone, phone));
        if(user == null) {
            user = userService.createUserWithPhone(phone);
        }
        return LoginStrategyHelper.loginSuccess(stringRedisTemplate, user);
    }
}
