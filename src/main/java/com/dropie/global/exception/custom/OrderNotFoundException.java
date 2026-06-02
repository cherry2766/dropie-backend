package com.dropie.global.exception.custom;

import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;

public class OrderNotFoundException extends BusinessException {
    public OrderNotFoundException() {
        super(ErrorCode.ORDER_NOT_FOUND);
    }
}
