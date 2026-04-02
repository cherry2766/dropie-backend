package com.dropie.exception.custom;

import com.dropie.exception.BusinessException;
import com.dropie.exception.ErrorCode;

public class UserNotFoundException extends BusinessException {

    public UserNotFoundException() {
        super(ErrorCode.USER_NOT_FOUND);
    }
}
