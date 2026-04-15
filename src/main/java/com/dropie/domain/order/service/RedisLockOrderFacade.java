package com.dropie.domain.order.service;

import com.dropie.domain.order.dto.request.CreateOrderRequest;
import com.dropie.domain.order.dto.response.OrderCreateResponse;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import com.dropie.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.lock.type", havingValue = "redis")
@RequiredArgsConstructor
public class RedisLockOrderFacade implements CreateOrderUseCase {

    private final OrderService orderService;
    private final RedissonClient redissonClient;

    @Override
    public OrderCreateResponse createOrder(CreateOrderRequest request, CustomUserDetails userDetails) {

        // 1. 상품 ID를 오름차순 정렬 → 데드락 방지
        //    Thread A가 상품 1→2 순, Thread B가 상품 2→1 순으로 락을 잡으면
        //    서로가 상대방의 락을 기다리며 영원히 대기하는 데드락 발생
        //    모든 스레드가 같은 순서(오름차순)로 락을 잡으면 이 상황이 만들어지지 않음
        List<Long> sortedProductIds = request.getItems().stream()
                .map(CreateOrderRequest.OrderItemRequest::getProductId)
                .sorted()
                .toList();

        List<RLock> locks = sortedProductIds.stream()
                .map(id -> redissonClient.getLock("stock:lock:" + id))
                .toList();

        // 2. MultiLock: 여러 상품의 락을 원자적으로 묶어서 획득/해제
        //    낱개로 잡으면 일부만 성공하고 나머지는 실패하는 상황이 생길 수 있음
        RLock multiLock = redissonClient.getMultiLock(locks.toArray(new RLock[0]));

        try {
            // 3. tryLock(waitTime, leaseTime, unit)
            //    waitTime(5초): 락 획득을 최대 5초 기다림. 초과 시 false 반환 → 503 응답
            //    leaseTime(3초): 락 최대 유지 시간
            //      - 서버가 락을 잡은 채로 죽어도 3초 후 Redis가 자동으로 락을 해제 (데드락 방지)
            //      - 주문 처리 평균 시간 ~200ms 기준으로 여유 있게 3초 설정
            //      - 너무 짧으면: 정상 처리 중인데 락이 풀려 다른 스레드가 진입
            //      - 너무 길면: 장애 시 서비스 중단 시간이 길어짐
            boolean acquired = multiLock.tryLock(5, 3, TimeUnit.SECONDS);

            if (!acquired) {
                log.warn("[RedisLock] 락 획득 실패 - userId={}, products={}",
                        userDetails.getUser().getId(), sortedProductIds);
                throw new BusinessException(ErrorCode.LOCK_ACQUISITION_FAILED);
            }

            log.debug("[RedisLock] 락 획득 성공 - userId={}", userDetails.getUser().getId());

            // 4. 락이 트랜잭션을 감싸야 한다 (순서가 매우 중요!)
            //
            //    올바른 순서 (현재 구조):
            //    [락 획득] → [트랜잭션 시작(@Transactional)] → [커밋] → [락 해제]
            //
            //    잘못된 순서 (@Transactional 안에서 락을 잡는 경우):
            //    [트랜잭션 시작] → [락 획득] → [락 해제] → [커밋]
            //    → 락 해제 후 커밋 전 사이에 다른 스레드가 아직 반영 안 된 재고를 읽을 수 있음
            //
            //    Facade 패턴으로 분리한 이유
            //    orderService.createOrder()는 @Transactional이 붙어 있어서
            //    이 메서드가 return될 때 = 커밋까지 완료된 상태
            return orderService.createOrder(request, userDetails);

        } catch (InterruptedException e) {
            // 스레드가 락 대기 중에 인터럽트를 받은 경우
            // JVM 규칙: catch 후 반드시 인터럽트 상태를 복구해야 이후 코드가 인터럽트를 인식할 수 있음
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.LOCK_ACQUISITION_FAILED);
        } finally {
            // 5. isHeldByCurrentThread() 확인 이유:
            //    leaseTime(3초) 초과로 Redis가 이미 자동 해제한 락에 unlock()을 호출하면
            //    IllegalMonitorStateException 발생
            //    → 현재 스레드가 실제로 보유 중일 때만 해제
            if (multiLock.isHeldByCurrentThread()) {
                multiLock.unlock();
                log.debug("[RedisLock] 락 해제 - userId={}", userDetails.getUser().getId());
            }
        }
    }
}
