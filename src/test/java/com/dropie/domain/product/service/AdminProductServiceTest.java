package com.dropie.domain.product.service;

import com.dropie.domain.event.entity.Event;
import com.dropie.domain.event.entity.EventStatus;
import com.dropie.domain.event.repository.EventRepository;
import com.dropie.domain.product.entity.Product;
import com.dropie.domain.product.dto.request.CreateProductRequest;
import com.dropie.domain.product.dto.request.UpdateProductRequest;
import com.dropie.domain.product.dto.request.UpdateProductStockRequest;
import com.dropie.domain.product.dto.response.ProductCreateResponse;
import com.dropie.domain.product.dto.response.ProductStockResponse;
import com.dropie.domain.product.repository.ProductRepository;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AdminProductServiceTest {

    @InjectMocks
    private AdminProductService adminProductService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private EventRepository eventRepository;

    private Event event;
    private Product product;

    @BeforeEach
    void setUp() {
        event = Event.builder()
                .brandName("노티드")
                .description("설명")
                .thumbnailImageUrl("https://thumb.jpg")
                .imageUrl("https://image.jpg")
                .startAt(LocalDateTime.of(2026, 4, 1, 20, 0))
                .endAt(LocalDateTime.of(2026, 4, 1, 22, 0))
                .status(EventStatus.UPCOMING)
                .build();

        product = Product.builder()
                .event(event)
                .name("초코두바이도넛")
                .imageUrl("https://product.jpg")
                .description("상품 설명")
                .price(5500)
                .stock(100)
                .build();
    }

    @Test
    @DisplayName("상품 등록 성공")
    void 상품_등록_성공() {
        // given
        CreateProductRequest request = new CreateProductRequest(1L, "초코두바이도넛", "https://product.jpg", "설명", 5500, 100);

        given(eventRepository.findById(1L)).willReturn(Optional.of(event));
        given(productRepository.save(any())).willReturn(product);

        // when
        ProductCreateResponse response = adminProductService.createProduct(request);

        // then
        assertThat(response.getName()).isEqualTo("초코두바이도넛");
        assertThat(response.getStock()).isEqualTo(100);
        then(productRepository).should().save(any(Product.class));
    }

    @Test
    @DisplayName("상품 등록 실패 - 없는 이벤트")
    void 상품_등록_없는이벤트_예외() {
        // given
        CreateProductRequest request = new CreateProductRequest(999L, "도넛", null, null, 5500, 100);

        given(eventRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminProductService.createProduct(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVENT_NOT_FOUND);
    }

    @Test
    @DisplayName("상품 수정 성공 - null 필드는 기존값 유지")
    void 상품_수정_성공() {
        // given
        // price만 수정, 나머지 null
        UpdateProductRequest request = UpdateProductRequest.builder()
                .price(6000)
                .build();

        given(productRepository.findById(1L)).willReturn(Optional.of(product));

        // when
        adminProductService.updateProduct(1L, request);

        // then
        assertThat(product.getPrice()).isEqualTo(6000);
        assertThat(product.getName()).isEqualTo("초코두바이도넛"); // 그대로 유지
    }

    @Test
    @DisplayName("재고 수정 성공")
    void 재고_수정_성공() {
        // given
        UpdateProductStockRequest request = new UpdateProductStockRequest(50);

        given(productRepository.findById(1L)).willReturn(Optional.of(product));

        // when
        ProductStockResponse response = adminProductService.updateStock(1L, request);

        // then
        assertThat(response.getStock()).isEqualTo(50);
        assertThat(product.getStock()).isEqualTo(50);
    }

    @Test
    @DisplayName("재고 수정 실패 - 음수 재고 400")
    void 재고_수정_음수_예외() {
        // given
        UpdateProductStockRequest request = new UpdateProductStockRequest(-1);

        given(productRepository.findById(1L)).willReturn(Optional.of(product));

        // when & then
        // Product.updateStock()에서 음수이면 INVALID_QUANTITY 예외
        assertThatThrownBy(() -> adminProductService.updateStock(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_QUANTITY);
    }

    @Test
    @DisplayName("상품 삭제 성공")
    void 상품_삭제_성공() {
        // given
        given(productRepository.findById(1L)).willReturn(Optional.of(product));

        // when
        adminProductService.deleteProduct(1L);

        // then
        then(productRepository).should().delete(product);
    }

    @Test
    @DisplayName("상품 수정 실패 - 없는 상품")
    void 상품_수정_없는상품_예외() {
        // given
        given(productRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                adminProductService.updateProduct(999L, UpdateProductRequest.builder().build()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("재고 수정 실패 - 없는 상품")
    void 재고_수정_없는상품_예외() {
        // given
        given(productRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                adminProductService.updateStock(999L, new UpdateProductStockRequest(50)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("상품 삭제 실패 - 없는 상품")
    void 상품_삭제_없는상품_예외() {
        // given
        given(productRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminProductService.deleteProduct(999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }
}
