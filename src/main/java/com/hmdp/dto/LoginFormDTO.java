package com.hmdp.dto;

import lombok.Data;

/**
 * @author MACHENIKE
 */
@Data
public class LoginFormDTO {
    private String phone;
    private String code;
    private String password;
    /**
     * 登录类型 0: 电话 + 验证码 1: 电话 + 密码
     */
    private Integer loginType;
}
