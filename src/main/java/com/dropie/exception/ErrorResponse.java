package com.dropie.exception;

import lombok.Getter;

@Getter
public class ErrorResponse {

    private final String code;
    private final String message;

    private ErrorResponse(ErrorCode errorCode) {
        this.code = errorCode.name();
        this.message = errorCode.getMessage();
    }

    // 생성자를 외부에서 못 쓰게 하고, of()로만 객체를 만들기 위한 메서드
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode);
    }
}
