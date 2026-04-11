package com.dropie.domain.user.dto.response;

import lombok.Builder;
import lombok.Getter;

// 내 정보 조회 API 응답
// 민감한 정보 포함 X
@Getter
@Builder
public class UserResponse {

    private Long id;
    private String email;
    private String nickname;
    private String role;
}
