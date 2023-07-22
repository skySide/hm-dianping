package com.hmdp.controller;


import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.UserInfo;
import com.hmdp.exception.BizException;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;


    /**
     * 发送手机验证码:对应的过程为:
     * 1、用户提交手机号到后台，尝试获取验证码
     * 2、后台接收到数据之后，首先需要判断手机号的格式是否正确
     * 3、如果不正确，那么就需要返回对应的提示信息
     * 4、否则，如果格式正确，那么就需要生成6位的验证码，然后
     * 将验证码保存到redis中，并且将验证码发送给用户。之所以需要
     * 将验证码保存到redis中，是因为当用户提交验证码登录
     * 之后，我们需要判断用户输入的验证码是否正确，而这时我们就需要
     * 从redis中取出正确的验证码
     */
    @PostMapping("/code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        return userService.sendCode(phone,session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm) throws BizException {
        Result result = userService.login(loginForm);
        return result;
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(HttpServletRequest request){
        // TODO 实现登出功能
        HttpSession session = request.getSession();
        session.invalidate();
        return Result.ok();
    }

    @GetMapping("/me")
    public Result me(){
        // 获取当前登录的用户并返回
        UserDTO userDTO = UserHolder.getUser();
        return Result.ok(userDTO);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(LocalDateTime.now());
        info.setUpdateTime(LocalDateTime.now());
        // 返回
        return Result.ok(info);
    }

    /**
     * 进入userId用户的主页的时候，需要查询这个用户的信息，并且显示在前端界面
     * @param userId
     * @return
     */
    @GetMapping("/{userId}")
    public Result queryById(@PathVariable("userId")Long userId){
        return userService.queryUserDTOById(userId);
    }

    /**
     * 实现用户签到功能
     * @return
     */
    @PostMapping("/sign")
    public Result sign(){
        return userService.sign();
    }

    /**
     * 获取用户从本月开始，到当前这一天为止的连续签到次数
     * @return
     */
    @GetMapping("/sign/count")
    public Result signCount(){
        return userService.signCount();
    }

}
