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

    // 프론트가 온보딩 페이지를 보여줄지 여부
    // → true: 온보딩으로 이동 / false: 홈으로 이동
    // → 백엔드에서 hasPreferences + onboardingSkipped 조합해서 계산한 결과값
    private boolean showOnboarding;
}
