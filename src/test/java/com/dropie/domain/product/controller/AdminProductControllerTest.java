package com.dropie.domain.product.controller;

import com.dropie.domain.product.dto.response.ProductCreateResponse;
import com.dropie.domain.product.dto.response.ProductStockResponse;
import com.dropie.domain.product.dto.response.ProductUpdateResponse;
import com.dropie.domain.product.service.AdminProductService;
import com.dropie.global.config.SecurityConfig;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
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
import org.springframework.data.redis.core.ValueOperations;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminProductController.class)
@Import(SecurityConfig.class)
class AdminProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminProductService adminProductService;

    // SecurityConfig 로드 시 JwtTokenProvider 빈이 필요 — 실제 JWT 동작 불필요, Mock으로 대체
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
