package com.dropie.domain.user.controller;

import com.dropie.domain.user.dto.response.UserResponse;
import com.dropie.domain.user.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import com.dropie.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // 내 정보 조회 API
    // GET /users/me
    // Header: Authorization: Bearer {토큰}
    // Response: { "id": 1, "email": "...", "nickname": "...", "role": "USER" }
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(@AuthenticationPrincipal CustomUserDetails userDetails) {
        log.debug("[GET/users/me] email: {}", userDetails.getUsername());

        return ResponseEntity.ok(userService.getMe(userDetails.getUsername()));
    }

    // 온보딩 스킵 API
    // → 스킵 버튼 클릭 시 호출, 이후 로그인부터 온보딩 미노출
    // → 인증된 유저만 호출 가능 (@AuthenticationPrincipal로 유저 식별)
    @PostMapping("/onboarding/skip")
    public ResponseEntity<Void> skipOnboarding(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        userService.skipOnboarding(userDetails.getUsername());
        return ResponseEntity.ok().build();
    }

    // 회원 탈퇴 API
    // DELETE /users/me
    // Header: Authorization: Bearer {액세스토큰}
    // Cookie: refreshToken={리프레시토큰}
    // Response: 204 No Content
    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletResponse response) {

        log.debug("[DELETE /users/me] 탈퇴 요청 - email: {}", userDetails.getUsername());

        // 유저 소프트 딜리트 + DB에서 Refresh Token 삭제
        userService.withdraw(userDetails.getUsername());

        // 쿠키 즉시 만료 처리
        // → maxAge(0) : 브라우저가 응답 받는 즉시 쿠키를 삭제함
        // → 로그아웃과 동일한 방식 — 클라이언트 쪽 인증 상태도 함께 초기화
        ResponseCookie expired = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(false) // 로컬 개발용, 배포 시 true로 변경
                .path("/auth")
                .maxAge(0)
                .sameSite("Strict")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expired.toString());

        return ResponseEntity.noContent().build();
    }
}
