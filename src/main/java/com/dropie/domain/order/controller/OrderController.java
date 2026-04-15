package com.dropie.domain.order.controller;

import com.dropie.domain.order.dto.request.CreateOrderRequest;
import com.dropie.domain.order.dto.response.OrderCancelResponse;
import com.dropie.domain.order.dto.response.OrderCreateResponse;
import com.dropie.domain.order.dto.response.OrderDetailResponse;
import com.dropie.domain.order.dto.response.OrderResponse;
import com.dropie.domain.order.service.CreateOrderUseCase;
import com.dropie.domain.order.service.OrderService;
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

    // POST /orders — 주문 등록
    // @AuthenticationPrincipal: Security 컨텍스트에서 현재 로그인 유저 꺼냄
    // @Valid: CreateOrderRequest 유효성 검사 실행
    @PostMapping
    public ResponseEntity<OrderCreateResponse> createOrder(
            @RequestBody @Valid CreateOrderRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.debug("[POST /orders] userId={}", userDetails.getUser().getId());
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
}
