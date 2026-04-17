package com.dropie.domain.auth.controller;

import com.dropie.domain.auth.dto.response.LoginResponse;
import com.dropie.domain.auth.service.AuthService;
import com.dropie.global.config.SecurityConfig;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import com.dropie.global.security.JwtTokenProvider;
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
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    // WebMvcConfig → RateLimitInterceptor → StringRedisTemplate 의존성 체인
    // @WebMvcTest는 Redis를 띄우지 않으므로 Mock으로 등록해야 컨텍스트 로드가 됨
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        // stringRedisTemplate은 Mock이라 opsForValue()가 기본적으로 null을 반환함
        // → RateLimitInterceptor에서 null.get() 호출 시 NPE 발생
        // → ValueOperations Mock을 직접 만들어서 반환하도록 설정
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        // null 반환 = Redis에 키 없음 = 첫 요청으로 간주 → rate limit 통과
        given(valueOperations.get(anyString())).willReturn(null);
    }

    @Test
    @DisplayName("POST /auth/signup - 성공 시 200과 accessToken 반환")
    void 회원가입_API_성공() throws Exception {
        // given
        // SecurityConfig에서 /auth/**는 permitAll() → @WithMockUser 불필요
        // SecurityConfig에서 csrf().disable() → .with(csrf()) 불필요
        given(authService.signUp(any()))
                .willReturn(LoginResponse.builder().accessToken("jwt.token.here").build());

        // when & then
        // mockMvc.perform() : 가상 HTTP 요청 실행
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)    // Content-Type: application/json
                        .content(
                                """
                                {
                                    "email": "test@email.com",
                                    "password": "password123",
                                    "nickname": "체리"
                                }
                                """))                               // 텍스트 블록(""")으로 JSON 직접 작성
                // andExpect() : 응답 결과 검증
                .andExpect(status().isCreated())             // HTTP 201인지 확인
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
                            "password": "password123",
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
                .willReturn(LoginResponse.builder().accessToken("jwt.token.here").build());

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

    @Test
    @DisplayName("POST /auth/signup - 비밀번호 8자 미만이면 400 반환")
    void 회원가입_비밀번호_너무짧음() throws Exception {
        // "pwd123"은 6자 → @Size(min=8) 위반
        // Bean Validation이 실패하면 서비스 호출 없이 Spring이 자동으로 400 반환
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
        // "passwordonly" → @Pattern 위반 (영문+숫자 조합 필수)
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
        // "체리!!" → @Pattern 위반 (한글/영문/숫자만 허용)
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
        // "이건이메일아님" → @Email 위반 (@가 없는 형식)
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

    @Test
    @DisplayName("GET /auth/verify-email - 유효한 토큰이면 200 반환")
    void 이메일_인증_성공() throws Exception {
        // given
        // verifyEmail()은 void 메서드 → willDoNothing()으로 정상 동작 세팅
        willDoNothing().given(authService).verifyEmail(anyString());

        // when & then
        mockMvc.perform(get("/auth/verify-email")
                        .param("token", "valid-token"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /auth/verify-email - 만료된 토큰이면 400 반환")
    void 이메일_인증_만료_토큰() throws Exception {
        // given
        // verifyEmail()이 예외를 던지도록 세팅
        // willThrow: void 메서드에 예외를 던지게 할 때 사용 (given().willReturn() 방식과 다름)
        willThrow(new BusinessException(ErrorCode.INVALID_VERIFICATION_TOKEN))
                .given(authService).verifyEmail(anyString());

        // when & then
        mockMvc.perform(get("/auth/verify-email")
                        .param("token", "expired-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
    }

}
