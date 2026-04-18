package com.dropie.domain.auth.controller;

import com.dropie.domain.auth.dto.request.LoginRequest;
import com.dropie.domain.auth.dto.request.SignUpRequest;
import com.dropie.domain.auth.dto.response.LoginResponse;
import com.dropie.domain.auth.service.AuthService;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final AuthService authService;

    // 회원가입 API
    // HttpServletResponse를 받는 이유:
    // → 서비스에서 Refresh Token을 Cookie로 설정할 때 response 객체에 직접 헤더를 추가해야 하기 때문
    @PostMapping("/signup")
    public ResponseEntity<LoginResponse> signup(
            @RequestBody @Valid SignUpRequest request,
            HttpServletResponse response) {
        LoginResponse loginResponse = authService.signUp(request, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(loginResponse);
    }

    // 로그인 API
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @RequestBody LoginRequest request,
            HttpServletResponse response) {
        LoginResponse loginResponse = authService.login(request, response);
        return ResponseEntity.ok(loginResponse);
    }

    // 토큰 재발급 API
    // @CookieValue : 요청 쿠키에서 "refreshToken" 값을 꺼내서 파라미터로 주입
    // required = false : 쿠키가 없어도 예외 발생 안 함 → null로 들어옴 → 아래에서 직접 체크
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {

        if (refreshToken == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        LoginResponse loginResponse = authService.refresh(refreshToken, response);
        return ResponseEntity.ok(loginResponse);
    }

    // 로그아웃 API
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {
        authService.logout(refreshToken, response);
        return ResponseEntity.noContent().build();
    }

    // 이메일 인증
    @GetMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok().build();
    }
}