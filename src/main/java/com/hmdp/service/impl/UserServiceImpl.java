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
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
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

import javax.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 利用正则表达式判断phone是否符合电话的格式,如果不符合，给出错误提示
        if(!RegexUtils.isPhoneValid(phone)){
            return Result.fail("电话不符合格式，请重新输入");
        }
        //电话符合，那么生成验证码
        String code = RandomUtil.randomNumbers(6);//验证码的长度为6
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
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1、校验手机号码的格式是否正确，这样是避免在发送一次验证码之后，修改了手机号的情况
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();
        if(!RegexUtils.isPhoneValid(phone)){
            return Result.fail("手机号码格式错误");
        }
        //2、判断验证码是否正确
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        if(cacheCode == null || !cacheCode.equals(code)){
            //不正确，那么给出提示信息，表示验证码错误
            return Result.fail("验证码错误");
        }
        //3、获取这个用户，如果找不到，那么就注册
        User user = getOne(new LambdaQueryWrapper<User>().eq(phone != null, User::getPhone, phone));
        if(user == null) {
            user = createUserWithPhone(phone);
        }
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
                        .setIgnoreNullValue(true) //是否忽略null的值
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
        return Result.ok(token);//将token发送给前端
    }

    public User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
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
