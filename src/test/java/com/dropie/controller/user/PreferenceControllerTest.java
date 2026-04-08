package com.dropie.controller.user;

import com.dropie.config.SecurityConfig;
import com.dropie.exception.BusinessException;
import com.dropie.exception.ErrorCode;
import com.dropie.security.JwtTokenProvider;
import com.dropie.service.preference.PreferenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PreferenceController.class)
@Import(SecurityConfig.class)
class PreferenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PreferenceService preferenceService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("POST /users/me/preferences - 성공 시 204 반환")
    @WithMockUser   // 인증된 유저 세팅
    void 취향_태그_등록_성공() throws Exception {
        // given
        // savePreferences()는 void 메서드 → willReturn() 대신 willDoNothing() 사용
        willDoNothing().given(preferenceService).savePreferences(any(), any());

        // when & then
        mockMvc.perform(post("/users/me/preferences")
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
    @WithMockUser
    void 잘못된_태그_아이디_실패() throws Exception {
        // given
        // void 메서드에서 예외를 던지는 경우 willThrow() 사용
        willThrow(new BusinessException(ErrorCode.TAG_NOT_FOUND))
                .given(preferenceService).savePreferences(any(), any());

        // when & then
        mockMvc.perform(post("/users/me/preferences")
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
