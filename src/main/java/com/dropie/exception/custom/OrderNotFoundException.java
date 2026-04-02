package com.dropie.exception.custom;

import com.dropie.exception.BusinessException;
import com.dropie.exception.ErrorCode;

public class OrderNotFoundException extends BusinessException {
    public OrderNotFoundException() {
        super(ErrorCode.ORDER_NOT_FOUND);
    }
}
