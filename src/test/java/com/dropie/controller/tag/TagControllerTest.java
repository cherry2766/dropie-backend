package com.dropie.controller.tag;

import com.dropie.config.SecurityConfig;
import com.dropie.domain.tag.Tag;
import com.dropie.dto.response.tag.TagResponse;
import com.dropie.security.JwtTokenProvider;
import com.dropie.service.tag.TagService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TagController.class)
@Import(SecurityConfig.class)
class TagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TagService tagService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("GET /tags - 성공 시 200과 태그 목록 반환")
    @WithMockUser // GET /tags는 인증 필요 → 가짜 유저로 인증된 상태 세팅
    void 태그_목록_조회_성공() throws Exception {
        // given
        // TagResponse는 Tag 엔티티 생성자만 있으므로 Tag.builder()로 Tag를 만든 후 TagResponse 생성
        List<TagResponse> responses = List.of(
                new TagResponse(Tag.builder().id(1L).name("달콤한").build()),
                new TagResponse(Tag.builder().id(2L).name("바삭한").build())
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