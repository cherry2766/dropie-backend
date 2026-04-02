package com.dropie.exception.custom;

import com.dropie.exception.BusinessException;
import com.dropie.exception.ErrorCode;

public class ProductNotFoundException extends BusinessException {

    public ProductNotFoundException() {
        super(ErrorCode.PRODUCT_NOT_FOUND);
    }
}
