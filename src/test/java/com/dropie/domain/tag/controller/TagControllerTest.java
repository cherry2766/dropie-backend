package com.dropie.domain.tag.controller;

import com.dropie.domain.tag.dto.response.TagResponse;
import com.dropie.domain.tag.entity.Tag;
import com.dropie.domain.tag.service.TagService;
import com.dropie.global.config.SecurityConfig;
import com.dropie.global.security.CustomUserDetailsService;
import com.dropie.global.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// TagController 레이어만 Spring에 올림
// Service는 @MockitoBean으로 대체 → 실제 비즈니스 로직 실행 없이 HTTP 동작만 검증
@WebMvcTest(TagController.class)
// SecurityConfig 명시적 로드 → csrf disable, 인증 필터 등 Security 설정 적용
@Import(SecurityConfig.class)
class TagControllerTest {

    // 실제 HTTP 서버 없이 컨트롤러에 가상 요청을 보내는 도구
    @Autowired
    private MockMvc mockMvc;

    // 실제 Service 대신 Mock으로 대체 — 원하는 응답을 직접 지정
    @MockitoBean
    private TagService tagService;

    // SecurityConfig가 로드될 때 JwtTokenProvider 빈이 필요함
    // 실제 JWT 동작은 필요 없고 빈 등록만 되면 되므로 Mock으로 대체
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    // WebMvcConfig → RateLimitInterceptor → StringRedisTemplate 의존성 체인
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);
    }

    @Test
    @DisplayName("GET /tags - 성공 시 200과 태그 목록 반환")
    @WithMockUser // GET /tags는 인증 필요 → 가짜 유저로 인증된 상태 세팅
    void 태그_목록_조회_성공() throws Exception {
        // given
        // TagResponse는 Tag 엔티티 생성자만 있으므로 Tag.builder()로 Tag를 만든 후 TagResponse 생성
        List<TagResponse> responses = List.of(
                TagResponse.from(Tag.builder().id(1L).name("달콤한").build()),
                TagResponse.from(Tag.builder().id(2L).name("바삭한").build())
        );
        given(tagService.getTags()).willReturn(responses);

        // when & then
        mockMvc.perform(get("/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("#달콤한"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].name").value("#바삭한"));
    }
}
