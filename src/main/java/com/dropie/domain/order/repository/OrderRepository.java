package com.dropie.domain.order.repository;

import com.dropie.domain.order.entity.Order;
import com.dropie.domain.order.entity.OrderStatus;
import com.dropie.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // 내 주문 목록 조회
    // → OrderItems + Product + Event 까지 한 번에 조회해 브랜드명 접근 시 N+1 방지
    // → DISTINCT: OrderItems ToMany 조인으로 같은 Order row가 중복 등장하는 걸 제거
    // → countQuery 분리: fetch join이 포함된 쿼리로는 count를 할 수 없으므로 단순 count 쿼리로 페이지 메타 계산
    @Query(value = "SELECT DISTINCT o FROM Order o " +
            "LEFT JOIN FETCH o.orderItems oi " +
            "LEFT JOIN FETCH oi.product p " +
            "LEFT JOIN FETCH p.event " +
            "WHERE o.user = :user",
            countQuery = "SELECT COUNT(o) FROM Order o WHERE o.user = :user")
    Page<Order> findByUserWithBrands(@Param("user") User user, Pageable pageable);

    // 주문 상세 조회
    @Query("SELECT o FROM Order o " +
            "LEFT JOIN FETCH o.orderItems oi " +
            "LEFT JOIN FETCH oi.product p " +
            "LEFT JOIN FETCH p.event " +
            "WHERE o.id = :orderId")
    Optional<Order> findByIdWithItems(@Param("orderId") Long orderId);

    // 자동 취소 처리 시 동시성 방어를 위한 비관적 락 조회
    // → SELECT ... FOR UPDATE
    // → confirmPayment 쪽도 동일 락으로 조회하도록 변경하면 완전한 직렬화 가능
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :orderId")
    Optional<Order> findByIdForUpdate(@Param("orderId") Long orderId);

    // 배치용: 오래된 PENDING 주문의 id만 조회
    // → fetch join이나 전체 엔티티 로딩을 피해 메모리 부담 최소화
    @Query("SELECT o.id FROM Order o " +
            "WHERE o.status = :status AND o.createdAt < :threshold")
    List<Long> findStalePendingOrderIds(
            @Param("status") OrderStatus status,
            @Param("threshold") LocalDateTime threshold);

    // 테스트용 쿼리 추가
    @Query("""
                select count(distinct o)
                from Order o
                join o.orderItems oi
                where oi.product.id = :productId
            """)
    long countByOrderedProductId(@Param("productId") Long productId);

    // 결제 완료 후 취향 태그 누적용 — Order + OrderItems + Product까지만 fetch
    // → productTags + tag는 동시 fetch 시 MultipleBagFetchException 발생 (List 두 개 동시 fetch 금지)
    // → Product.productTags에 @BatchSize 적용해 lazy 접근 시 IN 절로 일괄 로딩 → N+1 방지
    @Query("""
            SELECT DISTINCT o FROM Order o
            JOIN FETCH o.orderItems oi
            JOIN FETCH oi.product p
            WHERE o.id = :orderId
            """)
    Optional<Order> findByIdWithItemsForTagAccumulation(@Param("orderId") Long orderId);

    // 사용자의 PAID 상태 주문 전체에서 ProductTag.tagId 만 추출
    @Query("""
        SELECT pt.tag.id
        FROM Order o
        JOIN o.orderItems oi
        JOIN oi.product p
        JOIN p.productTags pt
        WHERE o.user.id = :userId
          AND o.status = com.dropie.domain.order.entity.OrderStatus.PAID
        """)
    List<Long> findPaidOrderTagIdsByUserId(@Param("userId") Long userId);
}
