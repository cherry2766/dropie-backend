package com.dropie.global.exception.custom;

import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;

public class OrderTimeException extends BusinessException {

    public OrderTimeException() {
        super(ErrorCode.ORDER_TIME_NOT_ALLOWED);
    }
}
