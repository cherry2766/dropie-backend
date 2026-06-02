package com.dropie.global.exception.custom;

import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;

public class AddressNotFoundException extends BusinessException {
    public AddressNotFoundException() {
        super(ErrorCode.ADDRESS_NOT_FOUND);
    }
}
