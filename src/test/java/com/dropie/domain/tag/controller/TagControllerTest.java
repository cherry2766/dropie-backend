package com.dropie.domain.tag.controller;

import com.dropie.domain.tag.dto.response.TagResponse;
import com.dropie.domain.tag.entity.Tag;
import com.dropie.domain.tag.service.TagService;
import com.dropie.global.config.SecurityConfig;
import com.dropie.global.security.CustomUserDetailsService;
import com.dropie.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// TagController 레이어만 Spring에 올림
// Service는 @MockitoBean으로 대체 → 실제 비즈니스 로직 실행 없이 HTTP 동작만 검증
//
// 두 엔드포인트:
//   1) GET /tags                  — 회원가입용. onboardingExposed=true만 노출
//   2) GET /admin/tags?keyword=   — 어드민 자동완성. 빈 keyword면 빈 배열
@WebMvcTest(TagController.class)
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

    // ===== GET /tags (회원가입 노출 태그) =====

    @Test
    @DisplayName("GET /tags - 성공 시 200과 회원가입 노출 태그 목록 반환")
    @WithMockUser // GET /tags는 인증 필요 → 가짜 유저로 인증된 상태 세팅
    void 회원가입_태그_목록_조회_성공() throws Exception {
        // given
        // Service가 onboardingExposed=true 태그만 골라서 #prefix 변환된 응답을 돌려줌
        List<TagResponse> responses = List.of(
                TagResponse.from(Tag.builder().id(1L).name("달콤한").build()),
                TagResponse.from(Tag.builder().id(2L).name("바삭한").build())
        );
        given(tagService.getOnboardingTags()).willReturn(responses);

        // when & then
        mockMvc.perform(get("/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("#달콤한"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].name").value("#바삭한"));
    }

    @Test
    @DisplayName("GET /tags - 노출 가능한 태그가 0개면 빈 배열 반환")
    @WithMockUser
    void 회원가입_태그_없으면_빈배열() throws Exception {
        // given
        // 운영자가 모든 큐레이션 태그를 onboardingExposed=false로 돌린 엣지 상황
        given(tagService.getOnboardingTags()).willReturn(List.of());

        // when & then
        mockMvc.perform(get("/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ===== GET /admin/tags?keyword= (어드민 자동완성) =====

    @Test
    @DisplayName("GET /admin/tags?keyword=초 - keyword 부분 일치 결과 반환")
    @WithMockUser(roles = "ADMIN")
    void 어드민_자동완성_정상() throws Exception {
        // given
        // "초"를 포함한 태그가 두 개 있다고 가정
        List<TagResponse> responses = List.of(
                TagResponse.from(Tag.builder().id(5L).name("초콜릿").build()),
                TagResponse.from(Tag.builder().id(11L).name("초코칩").build())
        );
        given(tagService.searchForAdmin("초")).willReturn(responses);

        // when & then
        mockMvc.perform(get("/admin/tags").param("keyword", "초"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("#초콜릿"))
                .andExpect(jsonPath("$[1].name").value("#초코칩"));

        // Service에 keyword가 그대로 전달되었는지 확인
        then(tagService).should().searchForAdmin("초");
    }

    @Test
    @DisplayName("GET /admin/tags?keyword= - keyword가 빈 문자열이면 빈 배열 반환")
    @WithMockUser(roles = "ADMIN")
    void 어드민_자동완성_빈문자열() throws Exception {
        // given
        // 사용자가 글자를 안 친 상태 → Service 정책상 빈 배열 반환
        given(tagService.searchForAdmin("")).willReturn(List.of());

        // when & then
        mockMvc.perform(get("/admin/tags").param("keyword", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /admin/tags - keyword 파라미터 누락 시 defaultValue=\"\" 적용되어 빈 배열")
    @WithMockUser(roles = "ADMIN")
    void 어드민_자동완성_keyword_누락() throws Exception {
        // given
        // @RequestParam(required = false, defaultValue = "") → keyword 미제공 시 ""로 들어감
        // → Service의 빈 입력 분기로 빠져 빈 배열 반환
        given(tagService.searchForAdmin("")).willReturn(List.of());

        // when & then
        mockMvc.perform(get("/admin/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // default="" 동작 검증 — 빈 문자열로 정확히 호출되었는지 확인
        then(tagService).should().searchForAdmin("");
    }

    @Test
    @DisplayName("GET /admin/tags?keyword=없는키워드 - 매칭 결과가 0개면 빈 배열 반환")
    @WithMockUser(roles = "ADMIN")
    void 어드민_자동완성_매칭_없음() throws Exception {
        // given
        // 키워드를 입력했지만 매칭되는 태그가 없는 엣지 (Service는 호출되되 빈 결과)
        given(tagService.searchForAdmin("없는키워드")).willReturn(List.of());

        // when & then
        mockMvc.perform(get("/admin/tags").param("keyword", "없는키워드"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        then(tagService).should().searchForAdmin("없는키워드");
    }
}
