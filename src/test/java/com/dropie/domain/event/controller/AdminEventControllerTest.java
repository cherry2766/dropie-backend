package com.dropie.domain.event.controller;

import com.dropie.domain.event.entity.EventStatus;
import com.dropie.domain.event.dto.response.EventCreateResponse;
import com.dropie.domain.event.dto.response.EventListResponse;
import com.dropie.domain.event.dto.response.EventStatusResponse;
import com.dropie.domain.event.dto.response.EventUpdateResponse;
import com.dropie.domain.event.service.AdminEventService;
import com.dropie.global.config.SecurityConfig;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import com.dropie.global.security.CustomUserDetailsService;
import com.dropie.global.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.redis.core.ValueOperations;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminEventController.class)
@Import(SecurityConfig.class)
class AdminEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminEventService adminEventService;

    // SecurityConfig 로드 시 JwtTokenProvider 빈이 필요 — 실제 JWT 동작 불필요, Mock으로 대체
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
    @DisplayName("GET /admin/events - 성공 시 200과 이벤트 목록 반환")
    @WithMockUser(roles = "ADMIN")
    void 이벤트_전체목록_조회_성공() throws Exception {
        // given
        List<EventListResponse> responses = List.of(
                EventListResponse.builder()
                        .id(1L).brandName("노티드").status(EventStatus.OPEN)
                        .startAt(LocalDateTime.of(2026, 4, 1, 20, 0))
                        .endAt(LocalDateTime.of(2026, 4, 1, 22, 0))
                        .build(),
                EventListResponse.builder()
                        .id(2L).brandName("크리스피크림").status(EventStatus.UPCOMING)
                        .startAt(LocalDateTime.of(2026, 5, 1, 20, 0))
                        .endAt(LocalDateTime.of(2026, 5, 1, 22, 0))
                        .build()
        );

        given(adminEventService.getEvents()).willReturn(responses);

        // when & then
        mockMvc.perform(get("/admin/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].brandName").value("노티드"))
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andExpect(jsonPath("$[1].id").value(2L))
                .andExpect(jsonPath("$[1].brandName").value("크리스피크림"))
                .andExpect(jsonPath("$[1].status").value("UPCOMING"));
    }

    @Test
    @DisplayName("GET /admin/events - 미인증 시 401")
    void 이벤트_전체목록_조회_미인증() throws Exception {
        // @WithMockUser 없음 → 401
        mockMvc.perform(get("/admin/events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /admin/events - 이벤트 없을 때 빈 배열 반환")
    @WithMockUser(roles = "ADMIN")
    void 이벤트_전체목록_조회_빈목록() throws Exception {
        // given
        given(adminEventService.getEvents()).willReturn(List.of());

        // when & then
        mockMvc.perform(get("/admin/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("POST /admin/events - 성공 시 201과 생성된 이벤트 반환")
    @WithMockUser(roles = "ADMIN")
        // ADMIN 권한 필요
    void 이벤트_등록_성공() throws Exception {
        // given
        EventCreateResponse response = EventCreateResponse.builder()
                .id(1L)
                .brandName("노티드")
                .status(EventStatus.UPCOMING)
                .build();

        given(adminEventService.createEvent(any())).willReturn(response);

        // when & then
        mockMvc.perform(post("/admin/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "brandName": "노티드",
                                    "description": "브랜드 설명",
                                    "thumbnailImageUrl": "https://thumb.jpg",
                                    "imageUrl": "https://image.jpg",
                                    "startAt": "2026-04-01T20:00:00",
                                    "endAt": "2026-04-01T22:00:00"
                                }
                                """))
                .andExpect(status().isCreated()) // 201
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.brandName").value("노티드"))
                .andExpect(jsonPath("$.status").value("UPCOMING"));
    }

    @Test
    @DisplayName("POST /admin/events - 미인증 시 401")
    void 이벤트_등록_미인증() throws Exception {
        // @WithMockUser 없음 → 401
        mockMvc.perform(post("/admin/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /admin/events - 필수 필드 누락 시 400")
    @WithMockUser(roles = "ADMIN")
    void 이벤트_등록_유효성검사_실패() throws Exception {
        // brandName 누락 → @NotBlank에 의해 400
        mockMvc.perform(post("/admin/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "description": "설명만"
                                }
                                """))
                .andExpect(status().isBadRequest()); // 400
    }

    @Test
    @DisplayName("PATCH /admin/events/{id} - 성공 시 200과 수정된 이벤트 반환")
    @WithMockUser(roles = "ADMIN")
    void 이벤트_수정_성공() throws Exception {
        // given
        EventUpdateResponse response = EventUpdateResponse.builder()
                .id(1L)
                .brandName("노티드")
                .status(EventStatus.UPCOMING)
                .build();

        given(adminEventService.updateEvent(eq(1L), any())).willReturn(response);

        // when & then
        mockMvc.perform(patch("/admin/events/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "description": "수정된 설명"
                                }
                                """))
                .andExpect(status().isOk()) // 200
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.brandName").value("노티드"));
    }

    @Test
    @DisplayName("PATCH /admin/events/{id} - 없는 이벤트 404")
    @WithMockUser(roles = "ADMIN")
    void 이벤트_수정_없는이벤트() throws Exception {
        // given
        given(adminEventService.updateEvent(eq(999L), any()))
                .willThrow(new BusinessException(ErrorCode.EVENT_NOT_FOUND));

        // when & then
        mockMvc.perform(patch("/admin/events/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "description": "수정"
                                }
                                """))
                .andExpect(status().isNotFound()) // 404
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("PATCH /admin/events/{id}/status - 성공 시 200과 변경된 상태 반환")
    @WithMockUser(roles = "ADMIN")
    void 이벤트_상태변경_성공() throws Exception {
        // given
        EventStatusResponse response = EventStatusResponse.builder()
                .id(1L)
                .status(EventStatus.OPEN)
                .build();

        given(adminEventService.changeEventStatus(eq(1L), any())).willReturn(response);

        // when & then
        mockMvc.perform(patch("/admin/events/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "status": "OPEN"
                                }
                                """))
                .andExpect(status().isOk()) // 200
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    @DisplayName("PATCH /admin/events/{id}/status - 허용되지 않는 전환 시 400")
    @WithMockUser(roles = "ADMIN")
    void 이벤트_상태변경_유효하지않은전환() throws Exception {
        // given
        // FINISHED → OPEN 같은 허용 불가 전환
        given(adminEventService.changeEventStatus(eq(1L), any()))
                .willThrow(new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION));

        // when & then
        mockMvc.perform(patch("/admin/events/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "status": "OPEN"
                                }
                                """))
                .andExpect(status().isBadRequest()) // 400
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("DELETE /admin/events/{id} - 성공 시 204")
    @WithMockUser(roles = "ADMIN")
    void 이벤트_삭제_성공() throws Exception {
        // given
        // void 반환 메서드는 willDoNothing()
        willDoNothing().given(adminEventService).deleteEvent(eq(1L));

        // when & then
        mockMvc.perform(delete("/admin/events/1"))
                .andExpect(status().isNoContent()); // 204
    }

    @Test
    @DisplayName("DELETE /admin/events/{id} - 없는 이벤트 404")
    @WithMockUser(roles = "ADMIN")
    void 이벤트_삭제_없는이벤트() throws Exception {
        // given
        willThrow(new BusinessException(ErrorCode.EVENT_NOT_FOUND))
                .given(adminEventService).deleteEvent(eq(999L));

        // when & then
        mockMvc.perform(delete("/admin/events/999"))
                .andExpect(status().isNotFound()) // 404
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }
}
