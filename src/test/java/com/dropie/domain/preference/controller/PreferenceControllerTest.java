package com.dropie.domain.preference.controller;

import com.dropie.domain.preference.service.PreferenceService;
import com.dropie.domain.user.entity.Role;
import com.dropie.domain.user.entity.User;
import com.dropie.global.config.SecurityConfig;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import com.dropie.global.security.CustomUserDetails;
import com.dropie.global.security.CustomUserDetailsService;
import com.dropie.global.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// PreferenceController 레이어만 Spring에 올림
// Service는 @MockitoBean으로 대체 → 실제 비즈니스 로직 실행 없이 HTTP 동작만 검증
@WebMvcTest(PreferenceController.class)
// SecurityConfig 명시적 로드 → csrf disable, 인증 필터 등 Security 설정 적용
@Import(SecurityConfig.class)
class PreferenceControllerTest {

    // 실제 HTTP 서버 없이 컨트롤러에 가상 요청을 보내는 도구
    @Autowired
    private MockMvc mockMvc;

    // 실제 Service 대신 Mock으로 대체 — 원하는 응답을 직접 지정
    @MockitoBean
    private PreferenceService preferenceService;

    // SecurityConfig가 로드될 때 JwtTokenProvider 빈이 필요함
    // 실제 JWT 동작은 필요 없고 빈 등록만 되면 되므로 Mock으로 대체
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    // WebMvcConfig → RateLimitInterceptor → StringRedisTemplate 의존성 체인
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

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
    @DisplayName("POST /users/me/preferences - 성공 시 204 반환")
    void 취향_태그_등록_성공() throws Exception {
        // given
        // savePreferences()는 void 메서드 → willReturn() 대신 willDoNothing() 사용
        willDoNothing().given(preferenceService).savePreferences(any(), any());

        // when & then
        mockMvc.perform(post("/users/me/preferences")
                        .with(user(mockUserDetails()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "tagIds": [1, 2, 3]
                                }
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /users/me/preferences - 존재하지 않는 tagId 시 404 반환")
    void 잘못된_태그_아이디_실패() throws Exception {
        // given
        // void 메서드에서 예외를 던지는 경우 willThrow() 사용
        willThrow(new BusinessException(ErrorCode.TAG_NOT_FOUND))
                .given(preferenceService).savePreferences(any(), any());

        // when & then
        mockMvc.perform(post("/users/me/preferences")
                        .with(user(mockUserDetails()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "tagIds": [1, 2, 999]
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TAG_NOT_FOUND"));
    }
}
