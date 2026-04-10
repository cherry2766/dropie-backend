package com.dropie.controller.admin;


import com.dropie.config.SecurityConfig;
import com.dropie.domain.enums.EventStatus;
import com.dropie.dto.response.event.EventCreateResponse;
import com.dropie.dto.response.event.EventStatusResponse;
import com.dropie.dto.response.event.EventUpdateResponse;
import com.dropie.dto.response.product.ProductCreateResponse;
import com.dropie.dto.response.product.ProductStockResponse;
import com.dropie.dto.response.product.ProductUpdateResponse;
import com.dropie.exception.BusinessException;
import com.dropie.exception.ErrorCode;
import com.dropie.security.JwtTokenProvider;
import com.dropie.service.admin.AdminEventService;
import com.dropie.service.admin.AdminProductService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminEventService adminEventService;

    @MockitoBean
    private AdminProductService adminProductService;

    // SecurityConfig 로드 시 JwtTokenProvider 빈이 필요 — 실제 JWT 동작 불필요, Mock으로 대체
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    // ===================== 이벤트 =====================

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

    // ===================== 상품 =====================

    @Test
    @DisplayName("POST /admin/products - 성공 시 201과 생성된 상품 반환")
    @WithMockUser(roles = "ADMIN")
    void 상품_등록_성공() throws Exception {
        // given
        ProductCreateResponse response = ProductCreateResponse.builder()
                .id(1L)
                .name("초코두바이도넛")
                .stock(100)
                .build();

        given(adminProductService.createProduct(any())).willReturn(response);

        // when & then
        mockMvc.perform(post("/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "eventId": 1,
                                    "name": "초코두바이도넛",
                                    "imageUrl": "https://image.jpg",
                                    "description": "상품 설명",
                                    "price": 5500,
                                    "stock": 100
                                }
                                """))
                .andExpect(status().isCreated()) // 201
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("초코두바이도넛"))
                .andExpect(jsonPath("$.stock").value(100));
    }

    @Test
    @DisplayName("POST /admin/products - 없는 이벤트 ID 404")
    @WithMockUser(roles = "ADMIN")
    void 상품_등록_없는이벤트() throws Exception {
        // given
        given(adminProductService.createProduct(any()))
                .willThrow(new BusinessException(ErrorCode.EVENT_NOT_FOUND));

        // when & then
        mockMvc.perform(post("/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "eventId": 999,
                                    "name": "도넛",
                                    "price": 5500,
                                    "stock": 100
                                }
                                """))
                .andExpect(status().isNotFound()) // 404
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("PATCH /admin/products/{id} - 성공 시 200과 수정된 상품 반환")
    @WithMockUser(roles = "ADMIN")
    void 상품_수정_성공() throws Exception {
        // given
        ProductUpdateResponse response = ProductUpdateResponse.builder()
                .id(1L)
                .name("초코두바이도넛")
                .price(6000)
                .build();

        given(adminProductService.updateProduct(eq(1L), any())).willReturn(response);

        // when & then
        mockMvc.perform(patch("/admin/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "price": 6000
                                }
                                """))
                .andExpect(status().isOk()) // 200
                .andExpect(jsonPath("$.price").value(6000));
    }

    @Test
    @DisplayName("PATCH /admin/products/{id}/stock - 성공 시 200과 재고 반환")
    @WithMockUser(roles = "ADMIN")
    void 상품_재고수정_성공() throws Exception {
        // given
        ProductStockResponse response = ProductStockResponse.builder()
                .id(1L)
                .stock(50)
                .build();

        given(adminProductService.updateStock(eq(1L), any())).willReturn(response);

        // when & then
        mockMvc.perform(patch("/admin/products/1/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "stock": 50
                                }
                                """))
                .andExpect(status().isOk()) // 200
                .andExpect(jsonPath("$.stock").value(50));
    }

    @Test
    @DisplayName("PATCH /admin/products/{id} - 없는 상품 404")
    @WithMockUser(roles = "ADMIN")
    void 상품_수정_없는상품() throws Exception {
        // given
        given(adminProductService.updateProduct(eq(999L), any()))
                .willThrow(new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        // when & then
        mockMvc.perform(patch("/admin/products/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "price": 6000
                                }
                                """))
                .andExpect(status().isNotFound()) // 404
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("PATCH /admin/products/{id}/stock - 음수 재고 400")
    @WithMockUser(roles = "ADMIN")
    void 상품_재고수정_음수() throws Exception {
        // given
        // Product.updateStock()에서 음수면 INVALID_QUANTITY 예외
        given(adminProductService.updateStock(eq(1L), any()))
                .willThrow(new BusinessException(ErrorCode.INVALID_QUANTITY));

        // when & then
        mockMvc.perform(patch("/admin/products/1/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "stock": -1
                                }
                                """))
                .andExpect(status().isBadRequest()) // 400
                .andExpect(jsonPath("$.code").value("INVALID_QUANTITY"));
    }

    @Test
    @DisplayName("DELETE /admin/products/{id} - 성공 시 204")
    @WithMockUser(roles = "ADMIN")
    void 상품_삭제_성공() throws Exception {
        // given
        willDoNothing().given(adminProductService).deleteProduct(eq(1L));

        // when & then
        mockMvc.perform(delete("/admin/products/1"))
                .andExpect(status().isNoContent()); // 204
    }

    @Test
    @DisplayName("DELETE /admin/products/{id} - 없는 상품 404")
    @WithMockUser(roles = "ADMIN")
    void 상품_삭제_없는상품() throws Exception {
        // given
        willThrow(new BusinessException(ErrorCode.PRODUCT_NOT_FOUND))
                .given(adminProductService).deleteProduct(eq(999L));

        // when & then
        mockMvc.perform(delete("/admin/products/999"))
                .andExpect(status().isNotFound()) // 404
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }
}