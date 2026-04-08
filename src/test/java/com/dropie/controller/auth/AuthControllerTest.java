package com.dropie.controller.auth;

import com.dropie.config.SecurityConfig;
import com.dropie.dto.response.auth.LoginResponse;
import com.dropie.exception.BusinessException;
import com.dropie.exception.ErrorCode;
import com.dropie.security.JwtTokenProvider;
import com.dropie.service.auth.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @WebMvcTest : Controller 레이어만 Spring에 띄움
// Service는 올라오지 않음 → @MockitoBean으로 대체해야 함
@WebMvcTest(AuthController.class)
// @Import : SecurityConfig를 명시적으로 로드
// → csrf disable, /auth/** permitAll 등 설정한 Security 규칙이 적용됨
@Import(SecurityConfig.class)
class AuthControllerTest {

    // 실제 HTTP 서버 없이 컨트롤러에 가상 요청을 보낼 수 있는 도구
    @Autowired
    private MockMvc mockMvc;

    // AuthService : 실제 Service 대신 Mock으로 대체
    @MockitoBean
    private AuthService authService;

    // JwtTokenProvider : SecurityConfig가 로드될 때 필요한 빈
    // 실제 동작은 필요 없고 빈으로만 등록되면 되므로 Mock으로 대체
    // → JWT 시크릿키 환경변수 없어도 됨
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("POST /auth/signup - 성공 시 200과 accessToken 반환")
    void 회원가입_API_성공() throws Exception {
        // given
        // SecurityConfig에서 /auth/**는 permitAll() → @WithMockUser 불필요
        // SecurityConfig에서 csrf().disable() → .with(csrf()) 불필요
        given(authService.signUp(any()))
                .willReturn(new LoginResponse("jwt.token.here"));

        // when & then
        // mockMvc.perform() : 가상 HTTP 요청 실행
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)    // Content-Type: application/json
                        .content(
                                """
                                {
                                    "email": "test@email.com",
                                    "password": "pwd123",
                                    "nickname": "체리"
                                }
                                """))                               // 텍스트 블록(""")으로 JSON 직접 작성
                // andExpect() : 응답 결과 검증
                .andExpect(status().isOk())                 // HTTP 200인지 확인
                .andExpect(jsonPath("$.accessToken").value("jwt.token.here")); // 응답 JSON의 accessToken 필드 확인
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
                .content(
                        """
                        {
                            "email": "test@email.com",
                            "password": "pwd123",
                            "nickname": "강아지"
                        }
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    @DisplayName("POST /auth/login - 성공 시 200과 accessToken 반환")
    void 로그인_API_성공() throws Exception {

        // given
        given(authService.login(any()))
                .willReturn(new LoginResponse("jwt.token.here"));

        // when & then
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                        {
                            "email": "test@email.com",
                            "password": "pwd123"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt.token.here"));
    }

    @Test
    @DisplayName("POST /auth/login - 이메일/비밀번호 불일치 시 401 반환")
    void 로그인_API_인증실패() throws Exception {
        // given
        given(authService.login(any()))
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

}