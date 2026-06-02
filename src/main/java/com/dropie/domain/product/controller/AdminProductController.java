package com.dropie.domain.product.controller;

import com.dropie.domain.product.dto.request.CreateProductRequest;
import com.dropie.domain.product.dto.request.UpdateProductRequest;
import com.dropie.domain.product.dto.request.UpdateProductStockRequest;
import com.dropie.domain.product.dto.response.AdminProductResponse;
import com.dropie.domain.product.dto.response.ProductCreateResponse;
import com.dropie.domain.product.dto.response.ProductStockResponse;
import com.dropie.domain.product.dto.response.ProductUpdateResponse;
import com.dropie.domain.product.service.AdminProductService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "관리자 - 상품", description = "상품 등록·수정·재고 관리·태그 자동 생성 (find-or-create)")
@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminProductController {

    private final AdminProductService adminProductService;

    // 상품 전체 목록 조회
    // GET /admin/products → 200
    @GetMapping("/products")
    public ResponseEntity<List<AdminProductResponse>> getProducts() {
        log.debug("[GET /admin/products]");
        return ResponseEntity.ok(adminProductService.getProducts());
    }

    // 상품 등록
    // POST /admin/products → 201
    @PostMapping("/products")
    public ResponseEntity<ProductCreateResponse> createProduct(
            @RequestBody @Valid CreateProductRequest request) {
        log.debug("[POST /admin/products] name: {}", request.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminProductService.createProduct(request));
    }

    // 상품 수정
    // PATCH /admin/products/{productId} → 200
    @PatchMapping("/products/{productId}")
    public ResponseEntity<ProductUpdateResponse> updateProduct(
            @PathVariable Long productId,
            @RequestBody UpdateProductRequest request) {
        log.debug("[PATCH /admin/products/{}]", productId);
        return ResponseEntity.ok(adminProductService.updateProduct(productId, request));
    }

    // 재고 수정
    // PATCH /admin/products/{productId}/stock → 200
    @PatchMapping("/products/{productId}/stock")
    public ResponseEntity<ProductStockResponse> updateStock(
            @PathVariable Long productId,
            @RequestBody @Valid UpdateProductStockRequest request) {
        log.debug("[PATCH /admin/products/{}/stock]", productId);
        return ResponseEntity.ok(adminProductService.updateStock(productId, request));
    }

    // 상품 삭제
    // DELETE /admin/products/{productId} → 204 (응답 바디 없음)
    @DeleteMapping("/products/{productId}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long productId) {
        log.debug("[DELETE /admin/products/{}]", productId);
        adminProductService.deleteProduct(productId);
        return ResponseEntity.noContent().build();
    }
}
