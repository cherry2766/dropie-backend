package com.dropie.domain.user.controller;

import com.dropie.domain.user.dto.response.UserResponse;
import com.dropie.domain.user.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
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
    public ResponseEntity<UserResponse> getMe(@AuthenticationPrincipal String email) {
        // @AuthenticationPrincipal : SecurityContext에 저장된 principal을 꺼내줌
        // JwtAuthenticationFilter에서 new UsernamePasswordAuthenticationToken(email, ...)
        // 으로 email을 principal에 넣었기 때문에 String으로 바로 받을 수 있음
        log.debug("[GET/users/me] email: {}", email);

        return ResponseEntity.ok(userService.getMe(email));
    }

    // 온보딩 스킵 API
    // → 스킵 버튼 클릭 시 호출, 이후 로그인부터 온보딩 미노출
    // → 인증된 유저만 호출 가능 (@AuthenticationPrincipal로 유저 식별)
    @PostMapping("/onboarding/skip")
    public ResponseEntity<Void> skipOnboarding(
            @AuthenticationPrincipal String email) {
        userService.skipOnboarding(email);
        return ResponseEntity.ok().build();
    }

    // 회원 탈퇴 API
    // DELETE /users/me
    // Header: Authorization: Bearer {액세스토큰}
    // Cookie: refreshToken={리프레시토큰}
    // Response: 204 No Content
    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(
            @AuthenticationPrincipal String email,
            HttpServletResponse response) {

        log.debug("[DELETE /users/me] 탈퇴 요청 - email: {}", email);

        // 유저 소프트 딜리트 + DB에서 Refresh Token 삭제
        userService.withdraw(email);

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
