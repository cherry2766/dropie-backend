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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "주문 · 결제", description = "동시성 제어 핵심 — Redisson 분산락 + 비관적 락 멱등 결제")
@Slf4j
@Validated
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService; // 조회/취소 (락 불필요)
    private final CreateOrderUseCase createOrderUseCase; // 주문 등록 전용 (락 Facade가 주입됨)
    private final PaymentService paymentService;

    @Operation(
            summary = "주문 등록",
            description = """
            선착순 주문을 **Redisson 분산락 기반**으로 처리합니다.
            - 다중 상품 주문은 MultiLock으로 원자 획득, productId 오름차순 정렬로 데드락 방지
            - tryLock(waitTime=5s, leaseTime=3s) — 서버 장애 시 락 자동 해제
            - 낙관적 락 구현체(OptimisticLockOrderFacade)도 함께 존재 — `app.lock.type` 프로퍼티로 토글(현재 redis)
            - 재고 0 전환 시 OPEN → SOLD_OUT 자동 전환
            - 주문번호는 Redis INCR로 발급 (다중 인스턴스 안전)
            """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "주문 생성 성공"),
            @ApiResponse(responseCode = "409", description = "OUT_OF_STOCK — 재고 부족 (낙관적 락 모드에선 ORDER_CONFLICT 추가)"),
            @ApiResponse(responseCode = "503", description = "LOCK_ACQUISITION_FAILED — 분산락 획득 실패 (waitTime 5s 초과)")
    })
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

    @Operation(
            summary = "주문 취소",
            description = "PENDING/PAID 상태의 주문만 취소 가능. 재고 복구 후 SOLD_OUT → OPEN 자동 전환.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "취소 성공"),
            @ApiResponse(responseCode = "403", description = "ORDER_ACCESS_DENIED — 본인 주문이 아님"),
            @ApiResponse(responseCode = "409", description = "이미 취소되었거나 COMPLETED 상태")
    })
    // PATCH /orders/{orderId}/cancel — 주문 취소
    @PatchMapping("/{orderId}/cancel")
    public ResponseEntity<OrderCancelResponse> cancelOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.debug("[PATCH /orders/{}/cancel]", orderId);
        return ResponseEntity.ok(orderService.cancelOrder(orderId, userDetails));
    }

    @Operation(
            summary = "토스페이먼츠 결제 승인",
            description = """
            프론트에서 받은 paymentKey + amount로 토스 승인 API를 호출해 Payment 엔티티를 저장합니다.
            승인 성공 시 OrderPaidEvent 발행 → AFTER_COMMIT + @Async로
            ZSET 취향 누적과 Claude 추천 갱신이 결제 트랜잭션과 분리되어 실행됩니다.
            """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "승인 완료"),
            @ApiResponse(responseCode = "409", description = "PAYMENT_ALREADY_PROCESSED — 이미 처리된 결제")
    })
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
