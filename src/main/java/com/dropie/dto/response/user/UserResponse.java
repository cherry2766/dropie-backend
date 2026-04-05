package com.dropie.dto.response.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 내 정보 조회 API 응답
// 민감한 정보 포함 X
@Getter
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String email;
    private String nickname;
    private String role;
}
