package com.dropie.dto.request.auth;

import lombok.Getter;

// 클라이언트가 POST /auth/signup 으로 보내는 JSON을 받는 객체
// {
//   "email": "test@test.com",
//   "password": "1234",
//   "nickname": "테스트"
// }
@Getter
public class SignUpRequest {

    private String email;
    private String password;
    private String nickname;
}
