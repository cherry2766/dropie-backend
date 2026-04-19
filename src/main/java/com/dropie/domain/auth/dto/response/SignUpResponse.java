package com.dropie.domain.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

// 회원가입 응답 DTO
@Getter
@Builder
public class SignUpResponse {

    // 프론트에 보여줄 안내 메시지
    // ex : "인증 이메일을 발송했습니다. 메일을 확인해 주세요"
    private String message;

    // 어느 이메일로 발송했는지 표시하기 위해 포함
    private String email;
}
