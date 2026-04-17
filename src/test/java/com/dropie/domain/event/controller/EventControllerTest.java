package com.dropie.domain.event.controller;

import com.dropie.domain.event.dto.response.EventDetailResponse;
import com.dropie.domain.event.dto.response.EventListResponse;
import com.dropie.domain.event.entity.EventStatus;
import com.dropie.domain.event.service.EventService;
import com.dropie.domain.product.dto.response.ProductResponse;
import com.dropie.global.common.PageResponse;
import com.dropie.global.config.SecurityConfig;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import com.dropie.global.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)  // EventController만 슬라이스 로드
@Import(SecurityConfig.class)       // Security 설정 적용
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

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
    @DisplayName("GET /events - 성공 시 200과 이벤트 목록 반환")
    void 이벤트_목록_조회_성공() throws Exception {
        // given
        // 컨트롤러는 서비스 결과를 그대로 반환하므로 서비스 반환값만 세팅
        PageResponse<EventListResponse> response = PageResponse.<EventListResponse>builder()
                .content(List.of(
                        EventListResponse.builder()
                                .id(1L)
                                .brandName("노티드")
                                .status(EventStatus.OPEN)
                                .startAt(LocalDateTime.of(2026, 4, 1, 20, 0))
                                .endAt(LocalDateTime.of(2026, 4, 1, 22, 0))
                                .build()
                ))
                .page(1)
                .size(6)
                .totalElements(1)
                .totalPages(1)
                .build();

        given(eventService.getEvents(1, 6)).willReturn(response);

        // when & then
        mockMvc.perform(get("/events")
                        .param("page", "1")
                        .param("size", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1L))
                .andExpect(jsonPath("$.content[0].brandName").value("노티드"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /events/{eventId} - 성공 시 200과 이벤트 상세 반환")
    void 이벤트_상세_조회_성공() throws Exception {
        // given
        PageResponse<ProductResponse> products = PageResponse.<ProductResponse>builder()
                .content(List.of(
                        ProductResponse.builder()
                                .id(1L)
                                .name("초코두바이도넛")
                                .description("상품 설명")
                                .price(5500)
                                .stock(30)
                                .build()
                ))
                .page(1)
                .size(5)
                .totalElements(1)
                .totalPages(1)
                .build();

        EventDetailResponse response = EventDetailResponse.builder()
                .id(1L)
                .brandName("노티드")
                .description("브랜드 설명")
                .status(EventStatus.OPEN)
                .startAt(LocalDateTime.of(2026, 4, 1, 20, 0))
                .endAt(LocalDateTime.of(2026, 4, 1, 22, 0))
                .products(products)
                .build();

        given(eventService.getEventDetail(1L, 1, 5)).willReturn(response);

        // when & then
        mockMvc.perform(get("/events/1")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.brandName").value("노티드"))
                .andExpect(jsonPath("$.products.content[0].name").value("초코두바이도넛"))
                .andExpect(jsonPath("$.products.content[0].description").value("상품 설명"));
    }

    @Test
    @DisplayName("GET /events/{eventId} - 없는 이벤트 404")
    void 이벤트_상세_조회_없는이벤트() throws Exception {
        // given
        given(eventService.getEventDetail(eq(999L), anyInt(), anyInt()))
                .willThrow(new BusinessException(ErrorCode.EVENT_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/events/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }
}