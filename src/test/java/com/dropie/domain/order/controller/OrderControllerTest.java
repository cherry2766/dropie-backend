package com.dropie.domain.order.controller;

import com.dropie.domain.order.dto.request.CreateOrderRequest;
import com.dropie.domain.order.dto.response.OrderCancelResponse;
import com.dropie.domain.order.dto.response.OrderCreateResponse;
import com.dropie.domain.order.dto.response.OrderDetailResponse;
import com.dropie.domain.order.dto.response.OrderResponse;
import com.dropie.domain.order.entity.OrderStatus;
import com.dropie.domain.order.service.CreateOrderUseCase;
import com.dropie.domain.order.service.OrderService;
import com.dropie.domain.user.entity.Role;
import com.dropie.domain.user.entity.User;
import com.dropie.global.common.PageResponse;
import com.dropie.global.config.SecurityConfig;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import com.dropie.global.security.CustomUserDetails;
import com.dropie.global.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// @WebMvcTest: 컨트롤러 레이어만 슬라이스 로드 (Service, Repository 등 빈 제외)
// @Import(SecurityConfig.class): Security 필터 체인을 실제로 적용해야 인증/인가 동작을 테스트할 수 있음
@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerTest {

    // MockMvc: HTTP 요청/응답을 실제 서버 없이 시뮬레이션하는 테스트용 객체
    @Autowired
    private MockMvc mockMvc;

    // ObjectMapper: 자바 객체 ↔ JSON 문자열 변환에 사용 (요청 바디 직렬화)
    @Autowired
    private ObjectMapper objectMapper;

    // @MockitoBean: 해당 빈을 Mockito Mock으로 교체해 Spring Context에 등록
    @MockitoBean
    private OrderService orderService;

    // CreateOrderUseCase: 주문 생성 전용 인터페이스 — 컨트롤러가 직접 호출하므로 Mock 필요
    @MockitoBean
    private CreateOrderUseCase createOrderUseCase;

    // JwtTokenProvider: SecurityConfig 내부에서 빈으로 주입받기 때문에 Mock 등록 필요
    // 없으면 SecurityConfig 로드 시 UnsatisfiedDependencyException 발생
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;


    // 테스트용 공통 픽스처 헬퍼

    // 인증된 유저의 CustomUserDetails 생성
    // SecurityMockMvcRequestPostProcessors.user()에 넘겨 @AuthenticationPrincipal에 주입
    private CustomUserDetails mockUserDetails() {
        User user = User.builder()
                .id(1L)
                .email("test@email.com")
                .nickname("테스터")
                .role(Role.USER)   // getAuthorities()에서 role.name()을 호출하므로 필수
                .build();
        return new CustomUserDetails(user);
    }

    // 기본 주문 생성 요청 객체 — 빌더 패턴으로 생성
    private CreateOrderRequest buildRequest() {
        return CreateOrderRequest.builder()
                .receiverName("홍길동")
                .phone("010-1234-5678")
                .zipcode("12345")
                .address1("서울시 강남구")
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder()
                                .productId(1L)
                                .quantity(2)
                                .build()
                ))
                .build();
    }

    // POST /orders — 주문 생성
    @Nested
    @DisplayName("POST /orders — 주문 생성")
    class CreateOrder {

        @Test
        @DisplayName("성공 시 201과 생성된 주문 정보 반환")
        void 주문_생성_성공() throws Exception {
            // given
            CreateOrderRequest request = buildRequest();

            // 서비스 계층의 응답값 스텁 설정
            OrderCreateResponse response = OrderCreateResponse.builder()
                    .orderId(1L)
                    .orderNumber("ORD-20260414-001")
                    .totalPrice(11000)
                    .status(OrderStatus.PENDING)
                    .build();

            // any(CreateOrderRequest.class): 요청 객체는 컨트롤러가 역직렬화해서 만들므로
            // 정확한 인스턴스를 특정할 수 없음 → any()로 매칭
            given(createOrderUseCase.createOrder(any(CreateOrderRequest.class), any(CustomUserDetails.class)))
                    .willReturn(response);

            // when & then
            mockMvc.perform(post("/orders")
                            // 인증된 유저를 Security 컨텍스트에 주입
                            .with(user(mockUserDetails()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated()) // 201
                    .andExpect(jsonPath("$.orderId").value(1L))
                    .andExpect(jsonPath("$.orderNumber").value("ORD-20260414-001"))
                    .andExpect(jsonPath("$.totalPrice").value(11000))
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }

        @Test
        @DisplayName("인증 없이 요청하면 401 반환")
        void 주문_생성_비인증_401() throws Exception {
            // given
            CreateOrderRequest request = buildRequest();

            // when & then
            // .with(user(...)) 없이 요청 → Security 필터가 401 반환
            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("receiverName 누락 시 400 반환")
        void 주문_생성_수신자이름_누락_400() throws Exception {
            // given
            // receiverName을 빈 문자열로 → @NotBlank 위반
            CreateOrderRequest request = CreateOrderRequest.builder()
                    .receiverName("")
                    .phone("010-1234-5678")
                    .zipcode("12345")
                    .address1("서울시 강남구")
                    .items(List.of(
                            CreateOrderRequest.OrderItemRequest.builder()
                                    .productId(1L)
                                    .quantity(2)
                                    .build()
                    ))
                    .build();

            // when & then
            mockMvc.perform(post("/orders")
                            .with(user(mockUserDetails()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("items 비어있으면 400 반환")
        void 주문_생성_아이템_없음_400() throws Exception {
            // given
            // items를 빈 리스트로 → @NotEmpty 위반
            CreateOrderRequest request = CreateOrderRequest.builder()
                    .receiverName("홍길동")
                    .phone("010-1234-5678")
                    .zipcode("12345")
                    .address1("서울시 강남구")
                    .items(List.of())
                    .build();

            // when & then
            mockMvc.perform(post("/orders")
                            .with(user(mockUserDetails()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("수량이 0 이하면 400 반환")
        void 주문_생성_수량_0이하_400() throws Exception {
            // given
            // quantity=0 → @Positive 위반
            CreateOrderRequest request = CreateOrderRequest.builder()
                    .receiverName("홍길동")
                    .phone("010-1234-5678")
                    .zipcode("12345")
                    .address1("서울시 강남구")
                    .items(List.of(
                            CreateOrderRequest.OrderItemRequest.builder()
                                    .productId(1L)
                                    .quantity(0)
                                    .build()
                    ))
                    .build();

            // when & then
            mockMvc.perform(post("/orders")
                            .with(user(mockUserDetails()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("이벤트 오픈 시간이 아니면 400 반환")
        void 주문_생성_이벤트시간외_400() throws Exception {
            // given
            given(createOrderUseCase.createOrder(any(CreateOrderRequest.class), any(CustomUserDetails.class)))
                    .willThrow(new BusinessException(ErrorCode.ORDER_TIME_NOT_ALLOWED));

            // when & then
            mockMvc.perform(post("/orders")
                            .with(user(mockUserDetails()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildRequest())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ORDER_TIME_NOT_ALLOWED"));
        }

        @Test
        @DisplayName("재고 부족 시 409 반환")
        void 주문_생성_재고부족_409() throws Exception {
            // given
            given(createOrderUseCase.createOrder(any(CreateOrderRequest.class), any(CustomUserDetails.class)))
                    .willThrow(new BusinessException(ErrorCode.OUT_OF_STOCK));

            // when & then
            mockMvc.perform(post("/orders")
                            .with(user(mockUserDetails()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildRequest())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("OUT_OF_STOCK"));
        }

        @Test
        @DisplayName("중복 상품 요청 시 400 반환")
        void 주문_생성_중복상품_400() throws Exception {
            // given
            given(createOrderUseCase.createOrder(any(CreateOrderRequest.class), any(CustomUserDetails.class)))
                    .willThrow(new BusinessException(ErrorCode.DUPLICATE_ORDER_ITEM));

            // when & then
            mockMvc.perform(post("/orders")
                            .with(user(mockUserDetails()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildRequest())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_ORDER_ITEM"));
        }
    }

    // GET /orders/me — 내 주문 목록 조회
    @Nested
    @DisplayName("GET /orders/me — 내 주문 목록 조회")
    class GetMyOrders {

        @Test
        @DisplayName("성공 시 200과 주문 목록 반환")
        void 주문_목록_조회_성공() throws Exception {
            // given
            PageResponse<OrderResponse> response = PageResponse.<OrderResponse>builder()
                    .content(List.of(
                            OrderResponse.builder()
                                    .orderId(1L)
                                    .orderNumber("ORD-20260414-001")
                                    .totalPrice(11000)
                                    .status(OrderStatus.PENDING)
                                    .createdAt(LocalDateTime.of(2026, 4, 14, 20, 0))
                                    .build()
                    ))
                    .page(1)
                    .size(10)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            given(orderService.getMyOrders(any(CustomUserDetails.class), eq(1), eq(10)))
                    .willReturn(response);

            // when & then
            mockMvc.perform(get("/orders/me")
                            .with(user(mockUserDetails()))
                            .param("page", "1")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].orderId").value(1L))
                    .andExpect(jsonPath("$.content[0].orderNumber").value("ORD-20260414-001"))
                    .andExpect(jsonPath("$.content[0].totalPrice").value(11000))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("인증 없이 요청하면 401 반환")
        void 주문_목록_조회_비인증_401() throws Exception {
            // when & then
            mockMvc.perform(get("/orders/me"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("page 파라미터 생략 시 기본값(1)으로 동작")
        void 주문_목록_조회_기본값_동작() throws Exception {
            // given
            // page/size 기본값: 1/10 (컨트롤러 @RequestParam defaultValue 설정)
            PageResponse<OrderResponse> response = PageResponse.<OrderResponse>builder()
                    .content(List.of())
                    .page(1)
                    .size(10)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            given(orderService.getMyOrders(any(CustomUserDetails.class), eq(1), eq(10)))
                    .willReturn(response);

            // when & then
            mockMvc.perform(get("/orders/me")
                            .with(user(mockUserDetails())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(1))
                    .andExpect(jsonPath("$.size").value(10));
        }

        @Test
        @DisplayName("page=0 (최솟값 위반) 시 400 반환")
        void 주문_목록_조회_page_0_400() throws Exception {
            // page=0 → @Min(1) 위반 → 400 (컨트롤러 레벨 @Validated 동작)
            mockMvc.perform(get("/orders/me")
                            .with(user(mockUserDetails()))
                            .param("page", "0"))
                    .andExpect(status().isBadRequest());
        }
    }

    // GET /orders/{orderId} — 주문 상세 조회
    @Nested
    @DisplayName("GET /orders/{orderId} — 주문 상세 조회")
    class GetOrderDetail {

        @Test
        @DisplayName("성공 시 200과 주문 상세 반환")
        void 주문_상세_조회_성공() throws Exception {
            // given
            OrderDetailResponse response = OrderDetailResponse.builder()
                    .orderId(1L)
                    .orderNumber("ORD-20260414-001")
                    .receiverName("홍길동")
                    .phone("010-1234-5678")
                    .address("서울시 강남구")
                    .totalPrice(11000)
                    .status(OrderStatus.PENDING)
                    .items(List.of(
                            OrderDetailResponse.OrderItemDetail.builder()
                                    .productId(1L)
                                    .productName("초코두바이도넛")
                                    .quantity(2)
                                    .orderPrice(5500)
                                    .build()
                    ))
                    .build();

            given(orderService.getOrderDetail(eq(1L), any(CustomUserDetails.class)))
                    .willReturn(response);

            // when & then
            mockMvc.perform(get("/orders/1")
                            .with(user(mockUserDetails())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderId").value(1L))
                    .andExpect(jsonPath("$.orderNumber").value("ORD-20260414-001"))
                    .andExpect(jsonPath("$.receiverName").value("홍길동"))
                    .andExpect(jsonPath("$.totalPrice").value(11000))
                    .andExpect(jsonPath("$.items[0].productName").value("초코두바이도넛"))
                    .andExpect(jsonPath("$.items[0].quantity").value(2));
        }

        @Test
        @DisplayName("인증 없이 요청하면 401 반환")
        void 주문_상세_조회_비인증_401() throws Exception {
            // when & then
            mockMvc.perform(get("/orders/1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("존재하지 않는 주문 ID 요청 시 404 반환")
        void 주문_상세_조회_없는주문_404() throws Exception {
            // given
            given(orderService.getOrderDetail(eq(999L), any(CustomUserDetails.class)))
                    .willThrow(new BusinessException(ErrorCode.ORDER_NOT_FOUND));

            // when & then
            mockMvc.perform(get("/orders/999")
                            .with(user(mockUserDetails())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        }

        @Test
        @DisplayName("다른 유저의 주문 조회 시 403 반환")
        void 주문_상세_조회_타유저_403() throws Exception {
            // given
            given(orderService.getOrderDetail(eq(1L), any(CustomUserDetails.class)))
                    .willThrow(new BusinessException(ErrorCode.FORBIDDEN));

            // when & then
            mockMvc.perform(get("/orders/1")
                            .with(user(mockUserDetails())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    // PATCH /orders/{orderId}/cancel — 주문 취소
    @Nested
    @DisplayName("PATCH /orders/{orderId}/cancel — 주문 취소")
    class CancelOrder {

        @Test
        @DisplayName("성공 시 200과 취소된 주문 상태 반환")
        void 주문_취소_성공() throws Exception {
            // given
            OrderCancelResponse response = OrderCancelResponse.builder()
                    .orderId(1L)
                    .status(OrderStatus.CANCELED)
                    .build();

            given(orderService.cancelOrder(eq(1L), any(CustomUserDetails.class)))
                    .willReturn(response);

            // when & then
            mockMvc.perform(patch("/orders/1/cancel")
                            .with(user(mockUserDetails())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderId").value(1L))
                    .andExpect(jsonPath("$.status").value("CANCELED"));
        }

        @Test
        @DisplayName("인증 없이 요청하면 401 반환")
        void 주문_취소_비인증_401() throws Exception {
            // when & then
            mockMvc.perform(patch("/orders/1/cancel"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("존재하지 않는 주문 취소 시 404 반환")
        void 주문_취소_없는주문_404() throws Exception {
            // given
            given(orderService.cancelOrder(eq(999L), any(CustomUserDetails.class)))
                    .willThrow(new BusinessException(ErrorCode.ORDER_NOT_FOUND));

            // when & then
            mockMvc.perform(patch("/orders/999/cancel")
                            .with(user(mockUserDetails())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        }

        @Test
        @DisplayName("이미 취소된 주문 재취소 시 400 반환")
        void 주문_취소_이미취소된주문_400() throws Exception {
            // given
            // CANCELED 상태 주문 → canCancel() false → CANCEL_NOT_ALLOWED 예외
            given(orderService.cancelOrder(eq(1L), any(CustomUserDetails.class)))
                    .willThrow(new BusinessException(ErrorCode.CANCEL_NOT_ALLOWED));

            // when & then
            mockMvc.perform(patch("/orders/1/cancel")
                            .with(user(mockUserDetails())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CANCEL_NOT_ALLOWED"));
        }

        @Test
        @DisplayName("구매 확정된 주문 취소 시 400 반환")
        void 주문_취소_완료된주문_400() throws Exception {
            // given
            // COMPLETED 상태 주문 → canCancel() false → CANCEL_NOT_ALLOWED 예외
            given(orderService.cancelOrder(eq(1L), any(CustomUserDetails.class)))
                    .willThrow(new BusinessException(ErrorCode.CANCEL_NOT_ALLOWED));

            // when & then
            mockMvc.perform(patch("/orders/1/cancel")
                            .with(user(mockUserDetails())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CANCEL_NOT_ALLOWED"));
        }

        @Test
        @DisplayName("다른 유저의 주문 취소 시 403 반환")
        void 주문_취소_타유저_403() throws Exception {
            // given
            given(orderService.cancelOrder(eq(1L), any(CustomUserDetails.class)))
                    .willThrow(new BusinessException(ErrorCode.FORBIDDEN));

            // when & then
            mockMvc.perform(patch("/orders/1/cancel")
                            .with(user(mockUserDetails())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }
}
