package com.dropie.domain.auth.controller;

import com.dropie.domain.auth.dto.request.LoginRequest;
import com.dropie.domain.auth.dto.request.SignUpRequest;
import com.dropie.domain.auth.dto.response.LoginResponse;
import com.dropie.domain.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// @Validated : 클래스 레벨에서 유효성 검증을 활성화
// @RestController : @Controller + @ResponseBody
// → 모든 메서드의 반환값이 JSON으로 직렬화되어 응답됨
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final AuthService authService;

    // 회원가입 API
    // POST /auth/signup
    // Body: { "email": "...", "password": "...", "nickname": "..." }
    // Response: { "accessToken": "eyJhbGci..." }
    // → 가입 즉시 토큰 발급해서 바로 로그인 상태로 만들어줌

    // @Valid : 요청 바디(SignUpRequest)에 달린 Bean Validation 어노테이션들을 실행
    // → 검증 실패 시 MethodArgumentNotValidException 발생
    //   → GlobalExceptionHandler에서 400 INVALID_INPUT으로 처리됨
    @PostMapping("/signup")
    public ResponseEntity<LoginResponse> signup(@RequestBody @Valid SignUpRequest request) {
        LoginResponse response = authService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 로그인 API
    // POST /auth/login
    // Body: { "email": "...", "password": "..." }
    // Response: { "accessToken": "eyJhbGci..." }
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    // GET /auth/verify-email?token=xxx
    // → 유저가 이메일의 인증 링크를 클릭했을 때 호출됨
    @GetMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok().build();
    }

}
