package com.hmdp.strategy.helper;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.exception.BizException;
import com.hmdp.service.IUserService;
import com.hmdp.strategy.LoginStrategy;
import com.hmdp.utils.RedisConstants;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author Administrator
 * @date 2023/7/22 16:57
 */
public class LoginStrategyHelper {

    public static Result login(LoginStrategy loginStrategy, LoginFormDTO loginFormDTO) throws BizException {
        return loginStrategy.login(loginFormDTO);
    }

    public static Result loginSuccess(StringRedisTemplate stringRedisTemplate, User user) {
        /*
        这个用户保存到redis中,这时候value是一个对象，尽管可以将这个
        对象转成json格式的字符串保存到redis中，但是如果涉及修改value
        中的某一个字段的时候，就会很麻烦，所以建议value是一个Hash类型
        的,并且占用的空间也会相对较少
         */
        //因为user的信息涉及到隐私信息，所以需要将一部分信息放在前端即可
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> map = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        //是否忽略null的值
                        .setIgnoreNullValue(true)
                        //将值转成String类型，因为利用的是stringRedisTemplate，key,value都是string类型的
                        //没有这一步，就可能导致类型转换错误
                        .setFieldValueEditor((String, Object) -> Object.toString())
        );
        //生成用户登录的token，作为key，将userDTO保存到redis中
        String token =  UUID.randomUUID(false).toString();
        String key = RedisConstants.LOGIN_TOKEN_KEY + token;
        /*
        Map<String, Object> map = BeanUtil.beanToMap(userDTO)这样写的话，那么执行下面的代码就会
        生报错,因为是利用stringRedisTemplate来进行操作的，所以key,value的序列化器都是string类型的，
        所以这时候就会发生报错，所以在调用beanToMap的时候，需要将对应的值转成string类型即可
        即
        Map<String, Object> map = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                //调用setFieldValueEditor，从而将值变成String类型
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((String,Object) -> Object.toString()));
         */
        stringRedisTemplate.opsForHash().putAll(key, map);
        //设置这个用户的有效期，当用户什么操作都不做的时候，时间到了就删除
        stringRedisTemplate.expire(key,RedisConstants.LOGIN_TOKEN_TTL, TimeUnit.MINUTES);
        return Result.ok(token);
    }
}
