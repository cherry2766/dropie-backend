package com.dropie.dto.response.auth;

import lombok.Builder;
import lombok.Getter;

// 로그인 성공 시 응답으로 내려주는 객체
// {
//   "accessToken": "eyJhbGci..."
// }
@Getter
@Builder
public class LoginResponse {

    private String accessToken;
}
