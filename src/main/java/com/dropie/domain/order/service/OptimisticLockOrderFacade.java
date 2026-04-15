package com.dropie.domain.order.service;

import com.dropie.domain.order.dto.request.CreateOrderRequest;
import com.dropie.domain.order.dto.response.OrderCreateResponse;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import com.dropie.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
// app.lock.type=optimistic 일 때만 Bean으로 등록됨
// ConditionalOnProperty: 특정 yml 설정값에 따라 Bean 등록 여부를 결정하는 어노테이션
@ConditionalOnProperty(name = "app.lock.type" , havingValue = "optimistic")
@RequiredArgsConstructor
public class OptimisticLockOrderFacade implements CreateOrderUseCase {

    private final OrderService orderService;

    // @Retryable: 지정한 예외가 발생하면 자동으로 재시도
    // retryFor: 낙관적 락 충돌 시 발생하는 예외 타입
    // maxAttempts = 3: 최대 3번 시도 (첫 번째 시도 포함)
    // backoff: 재시도 간격 — 100ms → 200ms → 400ms (multiplier = 2)
    //          지수 백오프(exponential backoff): 재시도가 동시에 몰리지 않도록 간격을 점점 늘림
    // @Retryable은 AOP 기반이라 같은 클래스 내 메서드 호출에서는 동작하지 않음 (Spring Proxy 한계)
    // 반드시 외부에서 이 메서드를 호출해야 재시도가 작동함

    @Override
    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            recover = "recover",   // 인터페이스 @Override 환경에서는 명시적으로 지정해야 자동 탐색 실패를 방지
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public OrderCreateResponse createOrder(CreateOrderRequest request, CustomUserDetails userDetails) {
        return orderService.createOrder(request, userDetails);
    }

    // @Recover: 재시도를 3번 모두 소진했을 때 실행되는 폴백 메서드
    // 첫 번째 파라미터는 반드시 처리할 예외 타입이어야 하고, 나머지는 원래 메서드와 동일해야 함
    // 여기까지 왔다는 건 충돌이 3번 연속 발생했다는 의미 → 409 응답 반환
    @Recover
    public OrderCreateResponse recover(ObjectOptimisticLockingFailureException e,
                                       CreateOrderRequest request,
                                       CustomUserDetails userDetails) {
        log.warn("[OptimisticLock] 재시도 소진 - userId={}", userDetails.getUser().getId());
        throw new BusinessException(ErrorCode.ORDER_CONFLICT);
    }
}
