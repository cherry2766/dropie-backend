package com.dropie.domain.user.controller;

import com.dropie.domain.user.dto.response.UserResponse;
import com.dropie.domain.user.entity.Role;
import com.dropie.domain.user.entity.User;
import com.dropie.domain.user.service.UserService;
import com.dropie.global.config.SecurityConfig;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import com.dropie.global.s3.PresignedUrlResponse;
import com.dropie.global.s3.S3Service;
import com.dropie.global.security.CustomUserDetails;
import com.dropie.global.security.CustomUserDetailsService;
import com.dropie.global.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// UserController 레이어만 Spring에 올림
// Service는 @MockitoBean으로 대체 → 실제 비즈니스 로직 실행 없이 HTTP 동작만 검증
@WebMvcTest(UserController.class)
// SecurityConfig 명시적 로드 → csrf disable, 인증 필터 등 Security 설정 적용
@Import(SecurityConfig.class)
class UserControllerTest {

    // 실제 HTTP 서버 없이 컨트롤러에 가상 요청을 보내는 도구
    @Autowired
    private MockMvc mockMvc;

    // 실제 Service 대신 Mock으로 대체 — 원하는 응답을 직접 지정
    @MockitoBean
    private UserService userService;

    // SecurityConfig가 로드될 때 JwtTokenProvider 빈이 필요함
    // 실제 JWT 동작은 필요 없고 빈 등록만 되면 되므로 Mock으로 대체
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    // WebMvcConfig → RateLimitInterceptor → StringRedisTemplate 의존성 체인
    // @WebMvcTest는 Redis를 띄우지 않으므로 Mock으로 등록해야 컨텍스트 로드가 됨
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private S3Service s3Service;

    private CustomUserDetails mockUserDetails() {
        User user = User.builder()
                .id(1L)
                .email("test@email.com")
                .nickname("테스터")
                .role(Role.USER)
                .build();
        return new CustomUserDetails(user);
    }

    @BeforeEach
    void setUp() {
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);
    }

    @Test
    @DisplayName("GET /users/me - 성공 시 200과 내 정보 반환")
    void 내정보_조회_API_성공() throws Exception {
        // given
        UserResponse response = UserResponse.builder()
                .id(1L)
                .email("test@email.com")
                .nickname("체리")
                .role("USER")
                .build();

        given(userService.getMe(any())).willReturn(response);

        // when & then
        // .with(user(...)): CustomUserDetails를 principal로 직접 주입
        // @WithMockUser는 Spring Security 기본 User를 넣어 CustomUserDetails로 못 받음
        mockMvc.perform(get("/users/me").with(user(mockUserDetails())))
                .andExpect(status().isOk()) // 200
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.email").value("test@email.com"))
                .andExpect(jsonPath("$.nickname").value("체리"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @DisplayName("GET /users/me - 미인증 시 401")
    void 내정보_조회_API_미인증() throws Exception {
        // @WithMockUser 없음 → Spring Security가 인증 실패로 401 반환
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized()); // 401
    }

    @Test
    @DisplayName("PATCH /users/me - 성공 시 200과 수정된 닉네임 반환")
    void 닉네임_수정_API_성공() throws Exception {
        // given
        UserResponse response = UserResponse.builder()
                .id(1L).email("test@email.com").nickname("딸기").role("USER").build();

        given(userService.updateNickname(eq("test@email.com"), any())).willReturn(response);

        // when & then
        mockMvc.perform(patch("/users/me")
                        .with(user(mockUserDetails()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "nickname": "딸기" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("딸기"));
    }

    @Test
    @DisplayName("PATCH /users/me - 닉네임 중복이면 409")
    void 닉네임_수정_중복_예외() throws Exception {
        // given
        given(userService.updateNickname(any(), any()))
                .willThrow(new BusinessException(ErrorCode.DUPLICATE_NICKNAME));

        // when & then
        mockMvc.perform(patch("/users/me")
                        .with(user(mockUserDetails()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "nickname": "딸기" }
                                """))
                .andExpect(status().isConflict()) // 409
                .andExpect(jsonPath("$.code").value("DUPLICATE_NICKNAME"));
    }

    @Test
    @DisplayName("PATCH /users/me - 닉네임 유효성 검사 실패 시 400 (빈 값)")
    void 닉네임_수정_유효성검사_실패() throws Exception {
        // given: nickname이 빈 문자열 → @NotBlank 위반
        mockMvc.perform(patch("/users/me")
                        .with(user(mockUserDetails()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "nickname": "" }
                                """))
                .andExpect(status().isBadRequest()); // 400
    }

    @Test
    @DisplayName("PATCH /users/me - 미인증 시 401")
    void 닉네임_수정_미인증() throws Exception {
        mockMvc.perform(patch("/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "nickname": "딸기" }
                                """))
                .andExpect(status().isUnauthorized()); // 401
    }

    @Test
    @DisplayName("POST /users/me/profile-image/presigned-url - 성공 시 200과 URL 반환")
    void 프로필이미지_presignedUrl_발급_성공() throws Exception {
        // given
        PresignedUrlResponse response = new PresignedUrlResponse(
                "https://s3.amazonaws.com/profiles/a1b2.jpg?X-Amz-Algorithm=...",
                "https://s3.amazonaws.com/profiles/a1b2.jpg"
        );

        given(s3Service.generatePresignedUrl(any(), any())).willReturn(response);

        // when & then
        mockMvc.perform(post("/users/me/profile-image/presigned-url")
                        .with(user(mockUserDetails()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "fileName": "profile.jpg", "contentType": "image/jpeg" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presignedUrl").exists())
                .andExpect(jsonPath("$.imageUrl").value("https://s3.amazonaws.com/profiles/a1b2.jpg"));
    }

    @Test
    @DisplayName("POST /users/me/profile-image/presigned-url - 미인증 시 401")
    void 프로필이미지_presignedUrl_미인증() throws Exception {
        mockMvc.perform(post("/users/me/profile-image/presigned-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "fileName": "profile.jpg", "contentType": "image/jpeg" }
                                """))
                .andExpect(status().isUnauthorized()); // 401
    }

    @Test
    @DisplayName("PATCH /users/me/profile-image - 성공 시 200과 수정된 유저 정보 반환")
    void 프로필이미지_저장_성공() throws Exception {
        // given
        UserResponse response = UserResponse.builder()
                .id(1L).email("test@email.com").nickname("체리")
                .profileImageUrl("https://s3.amazonaws.com/profiles/new.jpg").role("USER")
                .build();

        given(userService.updateProfileImage(eq("test@email.com"), any())).willReturn(response);

        // when & then
        mockMvc.perform(patch("/users/me/profile-image")
                        .with(user(mockUserDetails()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "profileImageUrl": "https://s3.amazonaws.com/profiles/new.jpg" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImageUrl").value("https://s3.amazonaws.com/profiles/new.jpg"));
    }

    @Test
    @DisplayName("PATCH /users/me/profile-image - 미인증 시 401")
    void 프로필이미지_저장_미인증() throws Exception {
        mockMvc.perform(patch("/users/me/profile-image")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "profileImageUrl": "https://s3.amazonaws.com/profiles/new.jpg" }
                                """))
                .andExpect(status().isUnauthorized()); // 401
    }
}
