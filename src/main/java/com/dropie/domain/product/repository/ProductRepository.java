package com.dropie.domain.product.repository;

import com.dropie.domain.event.entity.Event;
import com.dropie.domain.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // 특정 이벤트에 속한 상품 목록을 페이지네이션으로 조회
    // Spring Data JPA가 메서드 이름을 보고 자동으로 쿼리 생성:
    Page<Product> findByEvent(Event event, Pageable pageable);

    // 해당 이벤트에 재고가 남은 상품이 하나라도 있는지 확인
    // existsBy~: count(*) 대신 EXISTS 쿼리를 사용해서 성능이 더 좋음
    // → true면 아직 팔 수 있는 상품이 있음 / false면 전부 품절
    boolean existsByEventAndStockGreaterThan(Event event, int stock);
}
