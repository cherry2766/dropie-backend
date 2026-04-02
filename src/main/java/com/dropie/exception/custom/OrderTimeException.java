package com.dropie.exception.custom;

import com.dropie.exception.BusinessException;
import com.dropie.exception.ErrorCode;

public class OrderTimeException extends BusinessException {

    public OrderTimeException() {
        super(ErrorCode.ORDER_TIME_NOT_ALLOWED);
    }
}
