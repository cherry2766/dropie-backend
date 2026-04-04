package com.dropie.dto.request.auth;

import lombok.Getter;

// 클라이언트가 POST /auth/login 으로 보내는 JSON을 받는 객체
// {
//   "email": "test@test.com",
//   "password": "1234"
// }
@Getter
public class LoginRequest {

    private String email;
    private String password;
}
