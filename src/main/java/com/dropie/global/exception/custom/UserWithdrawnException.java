package com.dropie.global.exception.custom;

import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;

// 탈퇴한 계정이 로그인을 시도할 때 던지는 예외
// → BusinessException을 상속해서 GlobalExceptionHandler가 자동으로 에러 응답을 만들어줌
public class UserWithdrawnException extends BusinessException {

    public UserWithdrawnException() {
        super(ErrorCode.ACCOUNT_WITHDRAWN);
    }
}
