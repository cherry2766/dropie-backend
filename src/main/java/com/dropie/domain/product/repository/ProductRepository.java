package com.dropie.domain.product.repository;

import com.dropie.domain.event.entity.Event;
import com.dropie.domain.product.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // 특정 이벤트에 속한 상품 목록을 페이지네이션으로 조회
    // Spring Data JPA가 메서드 이름을 보고 자동으로 쿼리 생성:
    Page<Product> findByEvent(Event event, Pageable pageable);

    // 관리자용 전체 상품 목록 조회 — 이벤트 정보(brandName 등)를 한 번에 JOIN해서 가져옴
    // JOIN FETCH 없이 product.getEvent().getBrandName() 을 호출하면
    // 상품 수만큼 SELECT가 추가 발생하는 N+1 문제가 생김
    @Query("select p from Product p join fetch p.event")
    List<Product> findAllWithEvent();

    // 해당 이벤트에 재고가 남은 상품이 하나라도 있는지 확인
    // existsBy~: count(*) 대신 EXISTS 쿼리를 사용해서 성능이 더 좋음
    // → true면 아직 팔 수 있는 상품이 있음 / false면 전부 품절
    boolean existsByEventAndStockGreaterThan(Event event, int stock);

    // 여러 이벤트의 "재고 남음 여부"를 한 번의 쿼리로 조회 (N+1 방지)
    // 위 existsBy~를 목록에서 이벤트마다 호출하면 이벤트 N건당 쿼리가 N번 나가는 N+1이 발생함.
    // IN 절로 이벤트 id를 묶어서 "재고가 1개라도 남은 이벤트 id"만 한 번에 받아온 뒤,
    // 호출부에서 이 목록에 없는 이벤트를 품절로 판단한다.
    // distinct: 한 이벤트에 재고 있는 상품이 여러 개여도 id가 한 번만 나오게 함
    @Query("select distinct p.event.id from Product p where p.event.id in :eventIds and p.stock > 0")
    List<Long> findEventIdsHavingStock(@Param("eventIds") List<Long> eventIds);

    // @Lock(OPTIMISTIC): 이 메서드로 조회한 엔티티는 트랜잭션 커밋 시점에 version 검증 수행
    // 조회 후 커밋 전까지 다른 트랜잭션이 수정했으면 ObjectOptimisticLockingFailureException 발생
    // DB에 실제 락을 걸지 않으므로 조회 성능에는 영향 없음 — 커밋 시점에만 충돌 감지
    @Lock(LockModeType.OPTIMISTIC)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdWithOptimisticLock(@Param("id") Long id);
}
