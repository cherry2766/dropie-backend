package com.dropie.domain.product.service;

import com.dropie.domain.event.entity.Event;
import com.dropie.domain.product.entity.Product;
import com.dropie.domain.product.dto.request.CreateProductRequest;
import com.dropie.domain.product.dto.request.UpdateProductRequest;
import com.dropie.domain.product.dto.request.UpdateProductStockRequest;
import com.dropie.domain.product.dto.response.AdminProductResponse;
import com.dropie.domain.product.dto.response.ProductCreateResponse;
import com.dropie.domain.product.dto.response.ProductStockResponse;
import com.dropie.domain.product.dto.response.ProductUpdateResponse;
import com.dropie.domain.product.entity.ProductTag;
import com.dropie.domain.product.repository.ProductTagRepository;
import com.dropie.domain.tag.entity.Tag;
import com.dropie.domain.tag.repository.TagRepository;
import com.dropie.global.exception.custom.EventNotFoundException;
import com.dropie.global.exception.custom.ProductNotFoundException;
import com.dropie.domain.event.repository.EventRepository;
import com.dropie.domain.product.repository.ProductRepository;
import com.dropie.global.s3.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminProductService {

    private final ProductRepository productRepository;
    private final EventRepository eventRepository; // 상품 등록 시 이벤트 존재 여부 확인 필요
    private final TagRepository tagRepository;
    private final ProductTagRepository productTagRepository;
    private final S3Service s3Service;

    // 상품 전체 목록 조회 — 관리자 페이지에서 모든 이벤트의 상품을 한눈에 보여줄 때 사용
    // findAllWithEvent(): JOIN FETCH로 이벤트 정보를 한 번에 가져옴
    // → 이렇게 하지 않으면 product.getEvent().getBrandName() 호출 시 상품 수만큼 추가 쿼리 발생 (N+1)
    @Transactional(readOnly = true)
    public List<AdminProductResponse> getProducts() {
        log.debug("[getProducts] 상품 전체 목록 조회");
        return productRepository.findAllWithEvent().stream()
                .map(AdminProductResponse::from)
                .toList();
    }

    // 상품 등록 — 태그 이름이 있으면 find-or-create로 연결
    @Transactional
    public ProductCreateResponse createProduct(CreateProductRequest request) {
        log.debug("[createProduct] 등록 요청 - eventId: {}, name: {}", request.getEventId(), request.getName());

        // 상품이 속할 이벤트가 실제로 존재하는지 확인
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> {
                    log.warn("[createProduct] 이벤트 없음 - eventId: {}", request.getEventId());
                    return new EventNotFoundException();
                });

        Product product = Product.builder()
                .event(event)
                .name(request.getName())
                .imageUrl(request.getImageUrl())
                .description(request.getDescription())
                .price(request.getPrice())
                .stock(request.getStock())
                .build();
        productRepository.save(product);

        // 태그 연결 (있을때만)
        if (request.getTagNames() != null && !request.getTagNames().isEmpty()) {
            attachTags(product, request.getTagNames());
        }

        log.info("[createProduct] 등록 완료 - productId: {}", product.getId());
        return ProductCreateResponse.from(product);
    }

    // 상품 수정
    // @Transactional → dirty checking으로 save() 없이 변경사항 자동 반영
    @Transactional
    public ProductUpdateResponse updateProduct(Long productId, UpdateProductRequest request) {
        log.debug("[updateProduct] 수정 요청 - productId: {}", productId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> {
                    log.warn("[updateProduct] 상품 없음 - productId: {}", productId);
                    return new ProductNotFoundException();
                });

        product.update(
                request.getName(),
                request.getImageUrl(),
                request.getDescription(),
                request.getPrice()
        );

        // 태그 처리 (PATCH 규약)
        //   null     → 변경 없음 (아무것도 안 함)
        //   []       → 모두 제거
        //   값 있음  → 기존 다 지우고 새 목록으로 replace
        if (request.getTagNames() != null) {
            productTagRepository.deleteAllByProduct(product);
            productTagRepository.flush();

            if (!request.getTagNames().isEmpty()) {
                attachTags(product, request.getTagNames());
            }
        }

        log.info("[updateProduct] 수정 완료 - productId: {}", productId);
        return ProductUpdateResponse.from(product);
    }

    // 재고 수정
    // 음수 방어는 Product.updateStock() 내부에서 처리
    @Transactional
    public ProductStockResponse updateStock(Long productId, UpdateProductStockRequest request) {
        log.debug("[updateStock] 재고 수정 요청 - productId: {}", productId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> {
                    log.warn("[updateStock] 상품 없음 - productId: {}", productId);
                    return new ProductNotFoundException();
                });

        product.updateStock(request.getStock()); // 음수면 INVALID_QUANTITY 예외

        log.info("[updateStock] 재고 수정 완료 - productId: {}, stock: {}", productId, request.getStock());
        return ProductStockResponse.from(product);
    }

    // 상품 삭제
    @Transactional
    public void deleteProduct(Long productId) {
        log.debug("[deleteProduct] 삭제 요청 - productId: {}", productId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> {
                    log.warn("[deleteProduct] 상품 없음 - productId: {}", productId);
                    return new ProductNotFoundException();
                });

        // DB 삭제 전에 S3 이미지 먼저 삭제
        s3Service.deleteImage(product.getImageUrl());

        productRepository.delete(product);
        log.info("[deleteProduct] 삭제 완료 - productId: {}", productId);
    }

    // 태그 이름 → Tag 엔티티 변환 (find-or-create) → ProductTag 연결
    //
    // 동작:
    //   1) trim + 빈 문자열 거름 + 중복 입력 제거
    //   2) 같은 이름이 DB에 있으면 그 Tag 사용, 없으면 새로 생성
    //   3) 새로 생성하는 태그는 onboardingExposed=false (회원가입엔 안 노출)
    private void attachTags(Product product, List<String> rawTagNames) {
        List<String> normalized = rawTagNames.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();

        List<Tag> resolved = normalized.stream()
                .map(name -> tagRepository.findByName(name)
                        .orElseGet(() -> tagRepository.save(
                                Tag.builder()
                                        .name(name)
                                        .onboardingExposed(false)
                                        .build()
                        )))
                .toList();

        List<ProductTag> productTags = resolved.stream()
                .map(tag -> ProductTag.builder()
                        .product(product)
                        .tag(tag)
                        .build())
                .toList();
        productTagRepository.saveAll(productTags);
    }
}
