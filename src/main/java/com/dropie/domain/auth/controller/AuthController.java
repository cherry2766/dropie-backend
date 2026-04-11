package com.dropie.domain.auth.controller;

import com.dropie.domain.auth.dto.request.LoginRequest;
import com.dropie.domain.auth.dto.request.SignUpRequest;
import com.dropie.domain.auth.dto.response.LoginResponse;
import com.dropie.domain.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// @RestController : @Controller + @ResponseBody
// → 모든 메서드의 반환값이 JSON으로 직렬화되어 응답됨
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // 회원가입 API
    // POST /auth/signup
    // Body: { "email": "...", "password": "...", "nickname": "..." }
    // Response: { "accessToken": "eyJhbGci..." }
    // → 가입 즉시 토큰 발급해서 바로 로그인 상태로 만들어줌
    @PostMapping("/signup")
    public ResponseEntity<LoginResponse> signup(@RequestBody SignUpRequest request) {
        LoginResponse response = authService.signUp(request);
        return ResponseEntity.ok(response);
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

}
