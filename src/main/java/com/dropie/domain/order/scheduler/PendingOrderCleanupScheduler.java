package com.dropie.domain.order.scheduler;

import com.dropie.domain.order.entity.OrderStatus;
import com.dropie.domain.order.repository.OrderRepository;
import com.dropie.domain.order.service.PendingOrderAutoCancelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

// Redis 이벤트 유실 대비 안전망 배치
// → Redis 다운, Keyspace Notification 미전달, 앱 재시작 중 만료 등 시나리오를 커버
// → 주기적으로 "오래된 PENDING 주문"을 찾아 자동 취소 서비스로 위임
// 테스트 환경에서는 dropie.scheduler.pending-order-cleanup.enabled=false로 빈 등록 자체를 차단
// → 테스트 도중 PENDING 주문이 자동 취소되어 검증이 깨지는 flaky test 방지
// matchIfMissing=true: 운영 yml에 별도 설정 없어도 기본 활성화
@ConditionalOnProperty(
        name = "dropie.scheduler.pending-order-cleanup.enabled",
        havingValue = "true",
        matchIfMissing = true
)
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingOrderCleanupScheduler {

    private final OrderRepository orderRepository;
    private final PendingOrderAutoCancelService autoCancelService;

    // TTL보다 충분히 여유를 두고 조회 — "이미 TTL이 지났는데 아직 살아있는 주문"만 대상
    // TTL 15분 + 여유 2분 = 17분 이상 지난 PENDING
    // → 결제 진행 중인 주문을 잘못 취소하지 않기 위한 안전 마진
    private static final Duration STALE_THRESHOLD = Duration.ofMinutes(17);

    /**
     * 5분마다 실행되는 안전망 배치.
     * - fixedDelay: 이전 실행 종료 후 5분 대기 (중복 실행 방지)
     * - 평소엔 Redis 리스너가 다 처리하므로 이 배치의 조회 결과는 거의 비어있을 것
     * - Redis 장애 시에만 실제로 동작
     */

    @Scheduled(fixedDelay = 5 * 60 * 1000L) // 5분
    public void cleanupStalePendingOrders() {
        LocalDateTime threshold = LocalDateTime.now().minus(STALE_THRESHOLD);

        // created_at < threshold AND status = PENDING 인 주문 id만 조회
        // → 전체 엔티티를 로딩하지 않고 id만 가져와 루프에서 각각 트랜잭션으로 처리
        List<Long> staleOrderIds =
                orderRepository.findStalePendingOrderIds(OrderStatus.PENDING, threshold);

        if (staleOrderIds.isEmpty()) {
            // 조용히 종료 (평상시 로그 노이즈 방지)
            return;
        }

        log.warn("[PendingOrderCleanup] Redis 리스너가 놓친 PENDING 주문 발견 - count={}",
                staleOrderIds.size());

        // 각 주문을 개별 트랜잭션으로 처리
        // → 한 건 실패가 다른 건 처리를 막지 않음
        for (Long orderId : staleOrderIds) {
            try {
                autoCancelService.autoCancelIfPending(orderId);
            } catch (Exception e) {
                log.error("[PendingOrderCleanup] 자동 취소 실패 - orderId={}, error={}",
                        orderId, e.getMessage());
            }
        }
    }
}
