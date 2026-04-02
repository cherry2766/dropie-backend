package com.dropie.exception.custom;

import com.dropie.exception.BusinessException;
import com.dropie.exception.ErrorCode;

public class AddressNotFoundException extends BusinessException {
    public AddressNotFoundException() {
        super(ErrorCode.ADDRESS_NOT_FOUND);
    }
}
