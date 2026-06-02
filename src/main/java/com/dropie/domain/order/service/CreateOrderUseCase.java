package com.dropie.domain.order.service;

import com.dropie.domain.order.dto.request.CreateOrderRequest;
import com.dropie.domain.order.dto.response.OrderCreateResponse;
import com.dropie.global.security.CustomUserDetails;

// 전략 패턴 인터페이스
// OrderController는 주문 등록에서 이 인터페이스만 바라봄
// application.yml의 app.lock.type 값에 따라 구현체(Facade)가 자동으로 교체됨
// → yml 한 줄(app.lock.type: redis ↔ optimistic)만 바꾸면 코드 수정 없이 락 방식 전환 가능
public interface CreateOrderUseCase {

    OrderCreateResponse createOrder(CreateOrderRequest request, CustomUserDetails userDetails);
}
