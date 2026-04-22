package com.dropie.domain.order.repository;

import com.dropie.domain.order.entity.Order;
import com.dropie.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // 내 주문 목록 조회 — user 객체를 넘기면 JPA가 user_id 조건으로 변환
    Page<Order> findByUser(User user, Pageable pageable);

    // 주문 상세/취소 조회 시 N+1 방지용 fetch join
    // OrderItem 조회 시 Product도 한 번에 가져옴
    // LEFT JOIN FETCH: orderItems가 없는 주문도 누락 없이 조회
    @Query("SELECT o FROM Order o " +
            "LEFT JOIN FETCH o.orderItems oi " +
            "LEFT JOIN FETCH oi.product " +
            "WHERE o.id = :orderId")
    Optional<Order> findByIdWithItems(@Param("orderId") Long orderId);

    // 테스트용 쿼리 추가
    @Query("""
                select count(distinct o)
                from Order o
                join o.orderItems oi
                where oi.product.id = :productId
            """)
    long countByOrderedProductId(@Param("productId") Long productId);
}
