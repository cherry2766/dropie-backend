package com.dropie.domain.auth.controller;

import com.dropie.domain.auth.dto.request.*;
import com.dropie.domain.auth.dto.response.LoginResponse;
import com.dropie.domain.auth.dto.response.PasswordResetResponse;
import com.dropie.domain.auth.dto.response.SignUpResponse;
import com.dropie.domain.auth.service.AuthService;
import com.dropie.global.email.PasswordResetService;
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

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    // 회원가입 API
    // → HttpServletResponse 파라미터 제거: 쿠키 설정이 필요 없기 때문
    // → 반환 타입: LoginResponse → SignUpResponse (토큰 없이 메시지만 반환)
    @PostMapping("/signup")
    public ResponseEntity<SignUpResponse> signup(
            @RequestBody @Valid SignUpRequest request) {
        SignUpResponse response = authService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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

    // 이메일 인증 링크 처리 API
    // → 사용자가 메일 링크 클릭 시 이 API가 호출됨
    // → 토큰 검증 후 프론트 완료 페이지로 리다이렉트
    //
    // ResponseEntity 대신 void + HttpServletResponse를 직접 쓰는 이유:
    // → sendRedirect()는 HTTP 302 응답을 직접 쓰는 저수준 방식
    // → ResponseEntity를 리턴하면 Spring이 응답을 한 번 더 감싸서 충돌 발생 가능
    // → 이런 경우엔 response 객체를 직접 조작하는 것이 실무 관례
    @GetMapping("/verify-email")
    public void verifyEmail(
            @RequestParam String token,
            HttpServletResponse response) throws IOException {
        try {
            authService.verifyEmail(token);
            // 인증 성공 → 프론트 완료 페이지로 이동
            response.sendRedirect("http://localhost:5173/signup-complete?success=true");
        } catch (BusinessException e) {
            // 인증 실패 (토큰 만료 or 잘못된 토큰) → 실패 화면으로 이동
            response.sendRedirect("http://localhost:5173/signup-complete?success=false");
        }
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@RequestBody @Valid ResendVerificationRequest request) {
        authService.resendVerification(request.getEmail());
        return ResponseEntity.ok().build();
    }

    // 비밀번호 재설정 메일 발송 API
    // → 이메일이 존재하지 않아도 항상 200 응답 (이메일 열거 공격 방지)
    @PostMapping("/password-reset/request")
    public ResponseEntity<PasswordResetResponse> requestPasswordReset(
            @RequestBody @Valid PasswordResetRequest request) {
        passwordResetService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(PasswordResetResponse.builder()
                .message("비밀번호 재설정 메일을 발송했습니다. 메일함을 확인해 주세요.")
                .build());
    }

    // 새 비밀번호 설정 API
    // → 프론트에서 토큰(URL 파라미터)과 새 비밀번호를 함께 전송
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<PasswordResetResponse> resetPassword(
            @RequestBody @Valid PasswordResetConfirmRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(PasswordResetResponse.builder()
                .message("비밀번호가 성공적으로 변경되었습니다.")
                .build());
    }
}