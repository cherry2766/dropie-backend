package com.dropie.global.exception.custom;

import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;

public class EventNotFoundException extends BusinessException {

    public EventNotFoundException() {
        super(ErrorCode.EVENT_NOT_FOUND);
    }
}
