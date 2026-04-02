package com.dropie.exception.custom;

import com.dropie.exception.BusinessException;
import com.dropie.exception.ErrorCode;

public class EventNotFoundException extends BusinessException {

    public EventNotFoundException() {
        super(ErrorCode.EVENT_NOT_FOUND);
    }
}
