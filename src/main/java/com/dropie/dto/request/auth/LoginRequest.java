package com.dropie.dto.request.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 클라이언트가 POST /auth/login 으로 보내는 JSON을 받는 객체
// {
//   "email": "test@test.com",
//   "password": "1234"
// }
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class LoginRequest {

    private String email;
    private String password;
}
