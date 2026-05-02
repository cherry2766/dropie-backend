package com.dropie.domain.product.repository;

import com.dropie.domain.product.entity.Product;
import com.dropie.domain.product.entity.ProductTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductTagRepository extends JpaRepository<ProductTag, Long> {

    // 상품 수정 시 기존 태그 다 지우고 새 목록으로 replace
    void deleteAllByProduct(Product product);

    // 단일 상품 태그 조회 - 응답 DTO 만들 때 사용
    List<ProductTag> findAllByProduct(Product product);
}
