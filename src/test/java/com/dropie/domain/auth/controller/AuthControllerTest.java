package com.dropie.domain.auth.controller;

import com.dropie.domain.auth.dto.response.LoginResponse;
import com.dropie.domain.auth.dto.response.PasswordResetResponse;
import com.dropie.domain.auth.dto.response.SignUpResponse;
import com.dropie.domain.auth.service.AuthService;
import com.dropie.global.config.SecurityConfig;
import com.dropie.global.email.PasswordResetService;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import com.dropie.global.security.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    // WebMvcConfig → RateLimitInterceptor → StringRedisTemplate 의존성 체인
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        // null 반환 = Redis에 키 없음 = 첫 요청으로 간주 → rate limit 통과
        given(valueOperations.get(anyString())).willReturn(null);
    }

    // ===================== signUp =====================

    @Test
    @DisplayName("POST /auth/signup - 성공 시 201과 안내 메시지 반환")
    void 회원가입_API_성공() throws Exception {
        // given
        // signUp은 HttpServletResponse 파라미터 없음 (토큰 발급 안 하므로)
        given(authService.signUp(any()))
                .willReturn(SignUpResponse.builder()
                        .message("인증 이메일을 발송했습니다. 메일을 확인해 주세요.")
                        .email("test@email.com")
                        .build());

        // when & then
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "test@email.com",
                                    "password": "password123",
                                    "nickname": "체리"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("인증 이메일을 발송했습니다. 메일을 확인해 주세요."))
                .andExpect(jsonPath("$.email").value("test@email.com"));
    }

    @Test
    @DisplayName("POST /auth/signup - 이메일 중복 시 409 반환")
    void 회원가입_API_중복이메일_실패() throws Exception {
        // given
        given(authService.signUp(any()))
                .willThrow(new BusinessException(ErrorCode.DUPLICATE_EMAIL));

        // when & then
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "test@email.com",
                                    "password": "password123",
                                    "nickname": "강아지"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    @DisplayName("POST /auth/signup - 비밀번호 8자 미만이면 400 반환")
    void 회원가입_비밀번호_너무짧음() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "test@email.com",
                                    "password": "pwd123",
                                    "nickname": "체리"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("POST /auth/signup - 비밀번호에 숫자 없으면 400 반환")
    void 회원가입_비밀번호_숫자없음() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "test@email.com",
                                    "password": "passwordonly",
                                    "nickname": "체리"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("POST /auth/signup - 닉네임에 특수문자 있으면 400 반환")
    void 회원가입_닉네임_특수문자() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "test@email.com",
                                    "password": "password123",
                                    "nickname": "체리!!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("POST /auth/signup - 이메일 형식 오류면 400 반환")
    void 회원가입_이메일_형식오류() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "이건이메일아님",
                                    "password": "password123",
                                    "nickname": "체리"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    // ===================== login =====================

    @Test
    @DisplayName("POST /auth/login - 성공 시 200과 accessToken 반환")
    void 로그인_API_성공() throws Exception {
        // given
        // (LoginRequest, HttpServletResponse) 2개 인자를 받음
        given(authService.login(any(), any()))
                .willReturn(LoginResponse.builder().accessToken("jwt.token.here").role("USER").build());

        // when & then
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "test@email.com",
                                    "password": "pwd123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt.token.here"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @DisplayName("POST /auth/login - 이메일/비밀번호 불일치 시 401 반환")
    void 로그인_API_인증실패() throws Exception {
        // given
        given(authService.login(any(), any()))
                .willThrow(new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        // when & then
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "test@email.com",
                                    "password": "wrongPw"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    // ===================== refresh =====================

    @Test
    @DisplayName("POST /auth/refresh - 유효한 쿠키가 있으면 200과 새 accessToken 반환")
    void 토큰_재발급_API_성공() throws Exception {
        // given
        // 브라우저가 자동 첨부하는 쿠키를 MockMvc에서는 .cookie()로 직접 주입
        given(authService.refresh(eq("valid-refresh-token"), any()))
                .willReturn(LoginResponse.builder().accessToken("new-access-token").build());

        // when & then
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refreshToken", "valid-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"));
    }

    @Test
    @DisplayName("POST /auth/refresh - 쿠키 없으면 401 반환")
    void 토큰_재발급_API_쿠키없음() throws Exception {
        // given
        // 쿠키 없이 요청 → 컨트롤러에서 UNAUTHORIZED 예외 발생
        // when & then
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /auth/refresh - 유효하지 않은 토큰이면 401 반환")
    void 토큰_재발급_API_유효하지않은_토큰() throws Exception {
        // given
        given(authService.refresh(eq("invalid-token"), any()))
                .willThrow(new BusinessException(ErrorCode.INVALID_TOKEN));

        // when & then
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refreshToken", "invalid-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("POST /auth/refresh - 만료된 토큰이면 401 반환")
    void 토큰_재발급_API_만료된_토큰() throws Exception {
        // given
        given(authService.refresh(eq("expired-token"), any()))
                .willThrow(new BusinessException(ErrorCode.EXPIRED_TOKEN));

        // when & then
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refreshToken", "expired-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("EXPIRED_TOKEN"));
    }

    // ===================== logout =====================

    @Test
    @DisplayName("POST /auth/logout - 성공 시 204 반환")
    void 로그아웃_API_성공() throws Exception {
        // given
        // logout()은 void 메서드 → willDoNothing()으로 정상 동작 세팅
        willDoNothing().given(authService).logout(anyString(), any());

        // when & then
        mockMvc.perform(post("/auth/logout")
                        .cookie(new Cookie("refreshToken", "valid-token")))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /auth/logout - 쿠키 없어도 204 반환 (서버에서 null 처리)")
    void 로그아웃_API_쿠키없음() throws Exception {
        // given
        // 쿠키 없이 로그아웃 → 컨트롤러가 null 넘김 → 서비스에서 조용히 무시
        willDoNothing().given(authService).logout(any(), any());

        // when & then
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent());
    }

    // ===================== resendVerification =====================

    @Test
    @DisplayName("POST /auth/resend-verification - 성공 시 200 반환")
    void 인증메일_재발송_성공() throws Exception {
        // given
        willDoNothing().given(authService).resendVerification(anyString());

        // when & then
        mockMvc.perform(post("/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "test@email.com"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /auth/resend-verification - 쿨타임 내 재요청 시 429 반환")
    void 인증메일_재발송_쿨타임_초과() throws Exception {
        // given
        // EmailVerificationService에서 TOO_MANY_REQUESTS를 던지고 AuthService가 그대로 전파
        willThrow(new BusinessException(ErrorCode.TOO_MANY_REQUESTS))
                .given(authService).resendVerification(anyString());

        // when & then
        mockMvc.perform(post("/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "test@email.com"
                                }
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    // ===================== verifyEmail =====================

    @Test
    @DisplayName("GET /auth/verify-email - 유효한 토큰이면 프론트 완료 페이지로 302 리다이렉트")
    void 이메일_인증_성공() throws Exception {
        // given
        willDoNothing().given(authService).verifyEmail(anyString());

        // when & then
        // verifyEmail은 ResponseEntity 대신 sendRedirect()를 사용하므로 302 응답
        mockMvc.perform(get("/auth/verify-email")
                        .param("token", "valid-token"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("GET /auth/verify-email - 만료된 토큰이면 프론트 실패 페이지로 302 리다이렉트")
    void 이메일_인증_만료_토큰() throws Exception {
        // given
        // 예외가 발생해도 컨트롤러에서 catch 후 실패 URL로 리다이렉트하므로 302
        willThrow(new BusinessException(ErrorCode.INVALID_VERIFICATION_TOKEN))
                .given(authService).verifyEmail(anyString());

        // when & then
        mockMvc.perform(get("/auth/verify-email")
                        .param("token", "expired-token"))
                .andExpect(status().is3xxRedirection());
    }

    // ===================== password-reset/request =====================

    @Test
    @DisplayName("POST /auth/password-reset/request - 성공 시 200과 안내 메시지 반환")
    void 비밀번호_재설정_요청_성공() throws Exception {
        // given
        // requestPasswordReset은 void → 이메일 존재 여부 무관하게 200 반환
        willDoNothing().given(passwordResetService).requestPasswordReset(anyString());

        // when & then
        mockMvc.perform(post("/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "test@email.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("비밀번호 재설정 메일을 발송했습니다. 메일함을 확인해 주세요."));
    }

    @Test
    @DisplayName("POST /auth/password-reset/request - 이메일 형식 오류면 400 반환")
    void 비밀번호_재설정_요청_이메일_형식오류() throws Exception {
        mockMvc.perform(post("/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "이메일아님"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    // ===================== password-reset/confirm =====================

    @Test
    @DisplayName("POST /auth/password-reset/confirm - 성공 시 200과 완료 메시지 반환")
    void 비밀번호_재설정_확인_성공() throws Exception {
        // given
        willDoNothing().given(passwordResetService).resetPassword(anyString(), anyString());

        // when & then
        mockMvc.perform(post("/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "token": "valid-token",
                                    "newPassword": "newPwd1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("비밀번호가 성공적으로 변경되었습니다."));
    }

    @Test
    @DisplayName("POST /auth/password-reset/confirm - 토큰 없거나 만료됐으면 400 반환")
    void 비밀번호_재설정_확인_토큰_무효() throws Exception {
        // given
        willThrow(new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID))
                .given(passwordResetService).resetPassword(anyString(), anyString());

        // when & then
        mockMvc.perform(post("/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "token": "expired-token",
                                    "newPassword": "newPwd1234"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("POST /auth/password-reset/confirm - 비밀번호 형식 오류면 400 반환 (8자 미만)")
    void 비밀번호_재설정_확인_비밀번호_형식오류() throws Exception {
        mockMvc.perform(post("/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "token": "valid-token",
                                    "newPassword": "pwd1"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
