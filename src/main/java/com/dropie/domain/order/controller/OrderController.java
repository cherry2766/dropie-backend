package com.dropie.domain.order.controller;

import com.dropie.domain.order.dto.request.CreateOrderRequest;
import com.dropie.domain.order.dto.response.OrderCancelResponse;
import com.dropie.domain.order.dto.response.OrderCreateResponse;
import com.dropie.domain.order.dto.response.OrderDetailResponse;
import com.dropie.domain.order.dto.response.OrderResponse;
import com.dropie.domain.order.service.CreateOrderUseCase;
import com.dropie.domain.order.service.OrderService;
import com.dropie.domain.payment.dto.request.PaymentConfirmRequest;
import com.dropie.domain.payment.dto.response.PaymentConfirmResponse;
import com.dropie.domain.payment.service.PaymentService;
import com.dropie.global.common.PageResponse;
import com.dropie.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService; // 조회/취소 (락 불필요)
    private final CreateOrderUseCase createOrderUseCase; // 주문 등록 전용 (락 Facade가 주입됨)
    private final PaymentService paymentService;

    // POST /orders — 주문 등록
    // @AuthenticationPrincipal: Security 컨텍스트에서 현재 로그인 유저 꺼냄
    // @Valid: CreateOrderRequest 유효성 검사 실행
    @PostMapping
    public ResponseEntity<OrderCreateResponse> createOrder(
            @RequestBody @Valid CreateOrderRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        // 진입 로그 INFO: 요청이 백엔드에 도달했는지 1차 확인용
        // → 인증 필터까지 통과했다는 의미이므로 401 이슈 추적에도 도움됨
        log.info("[POST /orders] 진입 - userId={}, itemCount={}",
                userDetails.getUser().getId(), request.getItems().size());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(createOrderUseCase.createOrder(request, userDetails));
    }

    // GET /orders/me — 내 주문 목록 조회
    // page/size는 API 스펙 기본값 1, 10
    @GetMapping("/me")
    public ResponseEntity<PageResponse<OrderResponse>> getMyOrders(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) int size,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.debug("[GET /orders/me] userId={}", userDetails.getUser().getId());
        return ResponseEntity.ok(orderService.getMyOrders(userDetails, page, size));
    }

    // GET /orders/{orderId} — 주문 상세 조회
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailResponse> getOrderDetail(
            @PathVariable Long orderId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.debug("[GET /orders/{}]", orderId);
        return ResponseEntity.ok(orderService.getOrderDetail(orderId, userDetails));
    }

    // PATCH /orders/{orderId}/cancel — 주문 취소
    @PatchMapping("/{orderId}/cancel")
    public ResponseEntity<OrderCancelResponse> cancelOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.debug("[PATCH /orders/{}/cancel]", orderId);
        return ResponseEntity.ok(orderService.cancelOrder(orderId, userDetails));
    }

    /**
     * 결제 확인 (토스 승인 + 주문 PAID 전환)
     * <p>
     * 프론트가 토스 결제창 완료 후 받은 paymentKey와 amount를 백엔드로 보내면,
     * 백엔드가 토스 서버에 직접 승인 요청을 보내서 이중 검증함.
     */
    @PostMapping("/{orderId}/payment/confirm")
    public ResponseEntity<PaymentConfirmResponse> confirmPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long orderId,
            @RequestBody @Valid PaymentConfirmRequest request) {
        // 진입 로그 INFO: 프론트가 토스 결제 완료 후 백엔드 검증을 호출했는지 1차 확인
        // → 이 로그조차 안 찍히면 JWT 필터/Security에서 막히거나 프론트에서 호출 자체가 안 된 것
        log.info("[POST /orders/{}/payment/confirm] 진입 - userId={}, amount={}",
                orderId, userDetails.getUser().getId(), request.getAmount());

        return ResponseEntity.ok(
                paymentService.confirmPayment(userDetails.getUsername(), orderId, request)
        );
    }
}
