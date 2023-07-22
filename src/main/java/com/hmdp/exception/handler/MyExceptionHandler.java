package com.hmdp.exception.handler;

import com.hmdp.dto.Result;
import com.hmdp.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @author Administrator
 * @date 2023/7/22 20:45
 */
@RestControllerAdvice
@Slf4j
public class MyExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result handlerForBizException(BizException e) {
        return Result.fail(e.getMessage());
    }
}
