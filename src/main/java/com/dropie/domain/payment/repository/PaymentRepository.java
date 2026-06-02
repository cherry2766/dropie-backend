package com.dropie.domain.payment.repository;

import com.dropie.domain.order.entity.Order;
import com.dropie.domain.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // 주문에 연결된 결제가 이미 있는지 확인
    Optional<Payment> findByOrder(Order order);

    // 웹훅에서 paymentKey로 중복 이벤트 걸러낼 때 사용
    Optional<Payment> findByPaymentKey(String paymentKey);
}
