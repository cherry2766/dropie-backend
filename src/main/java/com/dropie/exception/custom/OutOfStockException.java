package com.dropie.exception.custom;

import com.dropie.exception.BusinessException;
import com.dropie.exception.ErrorCode;

public class OutOfStockException extends BusinessException {

    public OutOfStockException() {
        super(ErrorCode.OUT_OF_STOCK);
    }
}
