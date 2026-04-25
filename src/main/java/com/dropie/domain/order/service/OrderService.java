package com.dropie.domain.order.service;

import com.dropie.domain.event.entity.Event;
import com.dropie.domain.event.entity.EventStatus;
import com.dropie.domain.order.dto.request.CreateOrderRequest;
import com.dropie.domain.order.dto.response.OrderCancelResponse;
import com.dropie.domain.order.dto.response.OrderCreateResponse;
import com.dropie.domain.order.dto.response.OrderDetailResponse;
import com.dropie.domain.order.dto.response.OrderResponse;
import com.dropie.domain.order.entity.Order;
import com.dropie.domain.order.entity.OrderItem;
import com.dropie.domain.order.entity.OrderStatus;
import com.dropie.domain.order.repository.OrderRepository;
import com.dropie.domain.product.entity.Product;
import com.dropie.domain.product.repository.ProductRepository;
import com.dropie.domain.user.entity.User;
import com.dropie.global.common.PageResponse;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import com.dropie.global.exception.custom.OrderNotFoundException;
import com.dropie.global.exception.custom.ProductNotFoundException;
import com.dropie.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final StringRedisTemplate redisTemplate;

    // 주문번호 시퀀스 키 prefix — 일자별로 키가 분리됨 (예: order:seq:20260425)
    // → Redis INCR이 원자적으로 1씩 증가시키며, 같은 시퀀스가 두 번 발급되지 않음
    // → 서버 재시작/다중 인스턴스 환경에서도 시퀀스가 Redis에 보존되어 안전
    private static final String ORDER_SEQ_KEY_PREFIX = "order:seq:";
    // 시퀀스 키 TTL — 자정 이후엔 새 날짜 키로 전환되므로 어제 키는 자동 만료시켜 메모리 누수 방지
    private static final Duration ORDER_SEQ_TTL = Duration.ofDays(2);

    // 주문 TTL 상수
    private static final String PENDING_ORDER_KEY_PREFIX = "pending_order:";
    // ★ TTL을 15분으로 설정한 이유:
    //   - 토스 결제창 자체 타임아웃이 15분 → 그보다 짧게 잡으면
    //     정상 결제한 유저의 주문이 자동 취소되는 모순이 발생
    //   - 선착순 드롭 특성상 짧게 가져가고 싶지만
    //     사용자 신뢰를 우선해 토스와 동일하게 15분으로 설정
    private static final Duration PENDING_ORDER_TTL = Duration.ofMinutes(15);

    // POST /orders — 주문 등록
    // 흐름: 상품 조회 → 이벤트 판매 시간 검증 → 재고 차감 → 주문 저장
    // @Transactional: 재고 차감과 주문 저장이 하나의 트랜잭션
    //   → 중간 예외 발생 시 재고 차감도 함께 롤백됨
    @Transactional
    public OrderCreateResponse createOrder(CreateOrderRequest request, CustomUserDetails userDetails) {
        User user = userDetails.getUser();
        log.debug("[createOrder] userId={}, itemCount={}", user.getId(), request.getItems().size());

        // 동일 상품 중복 요청 방어
        // 같은 productId가 두 번 이상 들어오면 재고가 중복 차감될 수 있으므로 사전 차단
        List<Long> productIds = request.getItems().stream()
                .map(CreateOrderRequest.OrderItemRequest::getProductId)
                .toList();
        if (productIds.size() != new HashSet<>(productIds).size()) {
            throw new BusinessException(ErrorCode.DUPLICATE_ORDER_ITEM);
        }

        // Order 먼저 생성 (totalPrice는 루프 후 계산해서 덮어씀)
        Order order = Order.builder()
                .user(user)
                .orderNumber(generateOrderNumber())
                .receiverName(request.getReceiverName())
                .phone(request.getPhone())
                .zipcode(request.getZipcode())
                .address1(request.getAddress1())
                .address2(request.getAddress2())
                .totalPrice(0)
                .status(OrderStatus.PENDING)
                .build();

        int totalPrice = 0;

        // 주문된 상품이 속한 이벤트를 수집 (중복 제거)
        // Set을 쓰는 이유: 같은 이벤트의 상품을 여러 개 주문해도 품절 체크는 한 번만 하면 됨
        Set<Event> affectedEvents = new HashSet<>();

        // findByIdWithOptimisticLock()으로 조회해야 트랜잭션 커밋 시점에 version 검증이 수행됨
        for(CreateOrderRequest.OrderItemRequest req : request.getItems()) {
            Product product = productRepository.findByIdWithOptimisticLock(req.getProductId())
                    .orElseThrow(()-> {
                        log.warn("[createOrder] 상품 없음 - productId={}", req.getProductId());
                        return new ProductNotFoundException();
                    });

            validateEventTime(product.getEvent());
            product.decreaseStock(req.getQuantity());
            affectedEvents.add(product.getEvent());

            int itemPrice = product.getPrice() * req.getQuantity();
            totalPrice += itemPrice;

            order.addOrderItem(OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(req.getQuantity())
                    .orderPrice(itemPrice)
                    .build());
        }

        // 루프 끝나고 최종 totalPrice 반영
        order.updateTotalPrice(totalPrice);

        // 재고 차감 후 해당 이벤트의 모든 상품이 품절이면 자동 CLOSED 처리
        // dirty checking: @Transactional 안에서 상태만 바꾸면 save() 없이 트랜잭션 종료 시 자동 반영
        for (Event event : affectedEvents) {
            if (!productRepository.existsByEventAndStockGreaterThan(event, 0)) {
                event.changeStatus(EventStatus.CLOSED);
                log.info("[createOrder] 이벤트 전 상품 품절 — 자동 CLOSED 처리 eventId={}", event.getId());
            }
        }

        Order saved = orderRepository.save(order);
        log.info("[createOrder] 주문 완료 - orderId={}, orderNumber={}", saved.getId(), saved.getOrderNumber());

        // Redis에 자동 취소용 TTL 키 저장
        // → 15분 후 만료되면 Keyspace Notification이 발행되고,
        //   PendingOrderExpirationListener가 autoCancelIfPending(saved.getId())를 호출
        // → 값은 의미 없음, 키 자체의 존재와 TTL만 이용
        //
        // try-catch로 감싸는 이유:
        // → Redis 장애가 주문 트랜잭션까지 롤백시키면 안 됨 (외부 의존 장애 격리)
        // → Redis 등록이 실패해도 PendingOrderCleanupScheduler 배치(5분 주기)가
        //   stale PENDING 주문을 결국 정리하므로 안전망이 보장됨
        try {
            redisTemplate.opsForValue().set(
                    PENDING_ORDER_KEY_PREFIX + saved.getId(),
                    "1",
                    PENDING_ORDER_TTL
            );
            log.info("[createOrder] 자동 취소 TTL 등록 - orderId={}, ttl={}",
                    saved.getId(), PENDING_ORDER_TTL);
        } catch (Exception e) {
            log.error("[createOrder] Redis TTL 등록 실패 — 배치로 정리될 예정 - orderId={}",
                    saved.getId(), e);
        }

        return OrderCreateResponse.from(saved);
    }

    // GET /orders/me — 내 주문 목록 조회
    // 최신 주문이 먼저 오도록 createdAt 내림차순 정렬
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getMyOrders(CustomUserDetails userDetails, int page, int size) {
        User user = userDetails.getUser();
        log.debug("[getMyOrders] userId={}, page={}, size={}", user.getId(), page, size);

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(orderRepository.findByUserWithBrands(user, pageable).map(OrderResponse::from));
    }

    // GET /orders/{orderId} — 주문 상세 조회
    // findByIdWithItems: OrderItems + Product를 fetch join으로 한 번에 조회 (N+1 방지)
    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderDetail(Long orderId, CustomUserDetails userDetails) {
        log.debug("[getOrderDetail] orderId={}", orderId);

        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(()-> {
                    log.warn("[getOrderDetail] 주문 없음 - orderId={}", orderId);
                    return new OrderNotFoundException();
                });

        // 본인 주문인지 확인
        // 403 대신 404 반환: 타인 주문 존재 여부를 외부에 노출하지 않기 위함
        if (!order.getUser().getId().equals(userDetails.getUser().getId())) {
            throw new OrderNotFoundException();
        }

        return OrderDetailResponse.from(order);
    }

    // PATCH /orders/{orderId}/cancel — 주문 취소
    // 취소 시 재고 복구도 함께 처리
    @Transactional
    public OrderCancelResponse cancelOrder(Long orderId, CustomUserDetails userDetails) {
        log.debug("[cancelOrder] orderId={}", orderId);

        // 취소 시에도 OrderItems + Product가 필요 (재고 복구)하므로 fetch join 사용
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> {
                    log.warn("[cancelOrder] 주문 없음 - orderId={}", orderId);
                    return new OrderNotFoundException();
                });

        // 본인 주문인지 확인
        if (!order.getUser().getId().equals(userDetails.getUser().getId())) {
            throw new OrderNotFoundException();
        }

        // 취소 가능 여부 검증 + 상태 변경
        // 취소 불가 상태면 Order.cancel() 내부에서 CANCEL_NOT_ALLOWED 예외 발생
        order.cancel();

        // 재고 복구 — 취소된 수량만큼 stock 증가
        // dirty checking으로 별도 save() 없이 트랜잭션 종료 시 자동 반영
        order.getOrderItems().forEach(item ->
                item.getProduct().increaseStock(item.getQuantity()));

        log.info("[cancelOrder] 취소 완료 - orderId={}", orderId);
        return OrderCancelResponse.from(order);
    }

    // 이벤트 판매 시간 검증
    // 현재 시각이 startAt ~ endAt 사이인지 확인
    // 상태와 시간을 둘 다 검증하는 이유:
    //   상태가 OPEN이어도 관리자가 시간을 잘못 설정했을 수 있고,
    //   시간 범위 내여도 관리자가 강제로 CLOSED 처리했을 수 있음
    private void validateEventTime(Event event) {
        LocalDateTime now = LocalDateTime.now();

        // CLOSED/FINISHED 상태이거나 종료 시각을 지난 경우
        if(event.getStatus() == EventStatus.CLOSED
                || event.getStatus() == EventStatus.FINISHED
                || now.isAfter(event.getEndAt())) {
            throw new BusinessException(ErrorCode.EVENT_ENDED);
        }

        // UPCOMING 상태이거나 시작 시각 이전인 경우
        if (event.getStatus() == EventStatus.UPCOMING
                || now.isBefore(event.getStartAt())) {
            throw new BusinessException(ErrorCode.EVENT_NOT_STARTED);
        }
    }

    // 주문번호 생성 — "ORD-YYYYMMDD-000001" 형식
    // Redis INCR을 사용해 원자적으로 시퀀스를 발급
    // → 단일 스레드 모델인 Redis 특성상 같은 시퀀스가 두 번 발급되지 않음
    // → 서버 재시작/다중 인스턴스에서도 Redis에 상태가 보존되어 중복 충돌 없음
    // → 키가 처음 생성될 때(반환값이 1)에만 TTL을 걸어 다음날 자동 정리되도록 함
    private String generateOrderNumber() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String key = ORDER_SEQ_KEY_PREFIX + date;

        Long seq = redisTemplate.opsForValue().increment(key);
        // increment()는 키가 없으면 0에서 시작해 1을 반환 → 그 시점에만 TTL을 걸어주면 매번 expire를 호출하지 않아도 됨
        if (seq != null && seq == 1L) {
            redisTemplate.expire(key, ORDER_SEQ_TTL);
        }

        return String.format("ORD-%s-%06d", date, seq);
    }
}
