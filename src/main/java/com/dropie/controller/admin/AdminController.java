package com.dropie.controller.admin;

import com.dropie.dto.request.event.CreateEventRequest;
import com.dropie.dto.request.event.UpdateEventRequest;
import com.dropie.dto.request.event.UpdateEventStatusRequest;
import com.dropie.dto.request.product.CreateProductRequest;
import com.dropie.dto.request.product.UpdateProductRequest;
import com.dropie.dto.request.product.UpdateProductStockRequest;
import com.dropie.dto.response.event.EventCreateResponse;
import com.dropie.dto.response.event.EventStatusResponse;
import com.dropie.dto.response.event.EventUpdateResponse;
import com.dropie.dto.response.product.ProductCreateResponse;
import com.dropie.dto.response.product.ProductStockResponse;
import com.dropie.dto.response.product.ProductUpdateResponse;
import com.dropie.service.admin.AdminEventService;
import com.dropie.service.admin.AdminProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminEventService adminEventService;
    private final AdminProductService adminProductService;

    // ===================== 이벤트 =====================

    // 이벤트 등록
    // POST /admin/events → 201
    @PostMapping("/events")
    public ResponseEntity<EventCreateResponse> createEvent(
            @RequestBody @Valid CreateEventRequest request) {
        log.debug("[POST /admin/events] brandName: {}", request.getBrandName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminEventService.createEvent(request));
    }

    // 이벤트 수정
    // PATCH /admin/events/{eventId} → 200
    // PATCH는 변경할 필드만 포함 → @Valid 없음
    @PatchMapping("/events/{eventId}")
    public ResponseEntity<EventUpdateResponse> updateEvent(
            @PathVariable Long eventId,
            @RequestBody UpdateEventRequest request) {
        log.debug("[PATCH /admin/events/{}]", eventId);
        return ResponseEntity.ok(adminEventService.updateEvent(eventId, request));
    }

    // 이벤트 상태 변경
    // PATCH /admin/events/{eventId}/status → 200
    // 허용되지 않는 전환이면 400 INVALID_STATUS_TRANSITION (GlobalExceptionHandler에서 처리)
    @PatchMapping("/events/{eventId}/status")
    public ResponseEntity<EventStatusResponse> changeEventStatus(
            @PathVariable Long eventId,
            @RequestBody @Valid UpdateEventStatusRequest request) {
        log.debug("[PATCH /admin/events/{}/status] status: {}", eventId, request.getStatus());
        return ResponseEntity.ok(adminEventService.changeEventStatus(eventId, request));
    }

    // 이벤트 삭제
    // DELETE /admin/events/{eventId} → 204 (응답 바디 없음)
    @DeleteMapping("/events/{eventId}")
    public ResponseEntity<Void> deleteEvent(@PathVariable Long eventId) {
        log.debug("[DELETE /admin/events/{}]", eventId);
        adminEventService.deleteEvent(eventId);
        return ResponseEntity.noContent().build();
    }

    // ===================== 상품 =====================

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
