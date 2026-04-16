package com.dropie.domain.product.service;

import com.dropie.domain.event.entity.Event;
import com.dropie.domain.product.entity.Product;
import com.dropie.domain.product.dto.request.CreateProductRequest;
import com.dropie.domain.product.dto.request.UpdateProductRequest;
import com.dropie.domain.product.dto.request.UpdateProductStockRequest;
import com.dropie.domain.product.dto.response.ProductCreateResponse;
import com.dropie.domain.product.dto.response.ProductStockResponse;
import com.dropie.domain.product.dto.response.ProductUpdateResponse;
import com.dropie.global.exception.custom.EventNotFoundException;
import com.dropie.global.exception.custom.ProductNotFoundException;
import com.dropie.domain.event.repository.EventRepository;
import com.dropie.domain.product.repository.ProductRepository;
import com.dropie.global.s3.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminProductService {

    private final ProductRepository productRepository;
    private final EventRepository eventRepository; // 상품 등록 시 이벤트 존재 여부 확인 필요
    private final S3Service s3Service;

    // 상품 등록
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

        ProductCreateResponse response = ProductCreateResponse.from(productRepository.save(product));
        log.info("[createProduct] 등록 완료 - productId: {}", response.getId());
        return response;
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

}
