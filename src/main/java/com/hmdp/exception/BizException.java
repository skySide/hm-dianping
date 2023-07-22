package com.hmdp.exception;

/**
 * @author Administrator
 * @date 2023/7/22 20:44
 */
public class BizException extends Exception {
    public BizException(String errMsg) {
        super(errMsg);
    }
}
