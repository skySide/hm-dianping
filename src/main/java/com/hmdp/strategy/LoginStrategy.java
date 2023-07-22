package com.hmdp.strategy;

import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.exception.BizException;

/**
 * @author Administrator
 * @date 2023/7/22 16:49
 */
public interface LoginStrategy {
    /**
     * 登录校验相应的参数是否正确
     * @param loginFormDTO
     * @throws BizException
     * @return
     */
     Result login(LoginFormDTO loginFormDTO) throws BizException;
}
