package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.exception.BizException;
import org.springframework.dao.EmptyResultDataAccessException;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {
     Result sendCode(String phone, HttpSession session);

    User queryByPhone(String phone) throws EmptyResultDataAccessException;

    Result login(LoginFormDTO loginForm) throws BizException;

    Result queryUserDTOById(Long userId);

    Result sign();

    Result signCount();


    /**
     * 根据phone注册一个用户
     * @param phone
     */
    User createUserWithPhone(String phone);

    /**
     * 根据phone和password注册一个用户
     * @param phone
     * @param password
     * @return
     */
    User createUserWithPhoneAndPassword(String phone, String password);
}
