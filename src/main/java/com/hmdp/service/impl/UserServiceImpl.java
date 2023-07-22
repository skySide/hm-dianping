package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.exception.BizException;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.strategy.LoginStrategy;
import com.hmdp.strategy.helper.LoginStrategyHelper;
import com.hmdp.strategy.impl.PasswordLoginStrategy;
import com.hmdp.strategy.impl.VerifyCodeLoginStrategy;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private UserMapper userMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource(name = "passwordLoginStrategy")
    private LoginStrategy passwordLoginStrategy;

    @Resource(name = "verifyCodeLoginStrategy")
    private LoginStrategy verifyCodeLoginStrategy;

    private HashMap<Integer, LoginStrategy> loginStrategyMap;

    @PostConstruct
    private void populate() {
        loginStrategyMap = new HashMap<>(2);
        loginStrategyMap.put(0, verifyCodeLoginStrategy);
        loginStrategyMap.put(1, passwordLoginStrategy);
    }

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 利用正则表达式判断phone是否符合电话的格式,如果不符合，给出错误提示
        if(!RegexUtils.isPhoneValid(phone)){
            return Result.fail("电话不符合格式，请重新输入");
        }
        //电话符合，那么生成验证码,验证码的长度为6
        String code = RandomUtil.randomNumbers(6);
        /*
        将验证码保存到session中--->考虑到有多个tomcat服务器的时候，那么这时候
        就涉及到了session共享的问题，所以这时候需要将session保存到redis中
        这样就可以直接从redis中获取这个验证码,这时候key是电话号码，value就是验证码
        同时需要设置这个code的有效期,单位为分钟
        */
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone,code,
                RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //将发送验证码,将验证码以日志的形式输出
        log.debug("发送验证码{}",code);
        //返回成功发送
        return Result.ok(code);
    }

    @Override
    public Result login(LoginFormDTO loginForm) throws BizException {
        Integer loginType = loginForm.getLoginType();
        LoginStrategy loginStrategy = loginStrategyMap.get(loginType);
        if(Objects.isNull(loginStrategy)) {
            throw new BizException("登录类型loginType不可以为null");
        }
        return LoginStrategyHelper.login(loginStrategy, loginForm);
    }

    @Override
    public User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }

    @Override
    public User createUserWithPhoneAndPassword(String phone, String password) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        user.setPassword(password);
        save(user);
        return user;
    }

    @Override
    public User queryByPhone(String phone) throws EmptyResultDataAccessException{
       return userMapper.queryByPhone(phone);
    }

    /**
     * 查询某一个用户的信息
     * @param userId
     * @return
     */
    @Override
    public Result queryUserDTOById(Long userId) {
        User user = getById(userId);
        if(user == null){
            return Result.fail("用户不存在");
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class, "false");
        return Result.ok(userDTO);
    }

    /**
     * 实现用户签到功能，那么这时候需要利用到了bitmap数据结构
     * 通过命令setBit key offset value，来设置下标为offset的置为value
     * value只能为1、0.
     * 所以要统计某一个用户在哪一个时间签到，那么对应的key就是一个时间
     * 所以key = userId:currentDate
     * @return
     */
    @Override
    public Result sign() {
        //1、获取当前的登录用户
        Long userId = UserHolder.getUser().getId();
        //2、获取当前的时间
        LocalDateTime dateTime = LocalDateTime.now();
        String dateString = dateTime.format(DateTimeFormatter.ofPattern(":yyyy/MM"));
        //3、设置key
        String key = RedisConstants.USER_SIGN_KEY + userId + dateString;
        //4、获取offset，来设置key中的哪一个bit位的值,这时候getDayOfMonth是从1开始的，所以还需要减1
        int day = dateTime.getDayOfMonth();
        //5、通过opsForValue调用setBit来实现签到功能
        stringRedisTemplate.opsForValue().setBit(key, day - 1, true);
        return Result.ok();
    }

    /**
     * 获取当前用户到当前这一天为止的连续签到次数
     * 这时候需要利用到了命令bitfield，它是可以获取从offset开始的，
     * 长度为len的比特数字对应的十进制数字。
     * 所以这时候我们因为需要统计的是从第一天开始，到今天为止
     * 的连续签到次数，那么获取从0开始，长度为day的比特数字对应的十进制数字
     * 然后再和1进行与运算，从而可以得知当前这一天为止的连续签到次数
     * @return
     */
    @Override
    public Result signCount() {
        //1、获取当前的登录用户
        Long userId = UserHolder.getUser().getId();
        //2、 获取当前的日期
        LocalDateTime dateTime = LocalDateTime.now();
        String dateFormat = dateTime.format(DateTimeFormatter.ofPattern(":yyyy/MM"));
        //3、 获取签到对应的key
        String key = RedisConstants.USER_SIGN_KEY + userId + dateFormat;
        //4、 获取当前这一天是这个月份的第几天，对应的day就是我们需要统计多少个比特为的个数
        int day = dateTime.getDayOfMonth();
        //5、获取从0开始的，长度为day的二进制数字对应的无符号十进制数字
        //bitfield key get u[day] offset
        //之所以返回的是一个集合，因为BITFIELD也可同时进行其他的操作，例如SET，INCR
        List<Long> result = stringRedisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(day)).valueAt(0)
        );
        if(result == null || result.isEmpty()){
            return Result.ok(0);
        }
        //6、获取对应的无符号十进制数字
        Long num = result.get(0);
        if(num == 0L || num == null){
            return Result.ok(0);
        }
        int count = 0;
        System.out.println(Long.toBinaryString(num));
        //6.1 统计到今天为止的连续签到次数
        while(true){
            if((num & 1) == 0){
                //如果当前的比特位为0，说明没有签到，直接退出
                break;
            }else{
                ++count;
            }
            num >>>= 1;
        }
        return Result.ok(count);
    }
}
