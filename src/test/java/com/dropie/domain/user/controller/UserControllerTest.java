package com.dropie.domain.user.controller;

import com.dropie.domain.user.dto.response.UserResponse;
import com.dropie.domain.user.service.UserService;
import com.dropie.global.config.SecurityConfig;
import com.dropie.global.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Test
    @DisplayName("GET /users/me - 성공 시 200과 내 정보 반환")
    @WithMockUser // @WithMockUser → 가짜 인증 사용자로 요청 처리 (email은 "user"로 주입됨)
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
        mockMvc.perform(get("/users/me"))
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
}
