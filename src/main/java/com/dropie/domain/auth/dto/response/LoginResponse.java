package com.dropie.domain.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

// 로그인 성공 시 응답으로 내려주는 객체
// role을 포함해 프론트가 JWT 디코딩 없이 권한 분기 가능
@Getter
@Builder
public class LoginResponse {

    private String accessToken;
    private String role;
}
