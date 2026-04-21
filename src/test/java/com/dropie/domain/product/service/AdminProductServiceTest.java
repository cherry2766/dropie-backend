package com.dropie.domain.product.service;

import com.dropie.domain.event.entity.Event;
import com.dropie.domain.event.entity.EventStatus;
import com.dropie.domain.event.repository.EventRepository;
import com.dropie.domain.product.entity.Product;
import com.dropie.domain.product.dto.request.CreateProductRequest;
import com.dropie.domain.product.dto.request.UpdateProductRequest;
import com.dropie.domain.product.dto.request.UpdateProductStockRequest;
import com.dropie.domain.product.dto.response.AdminProductResponse;
import com.dropie.domain.product.dto.response.ProductCreateResponse;
import com.dropie.domain.product.dto.response.ProductStockResponse;
import com.dropie.domain.product.repository.ProductRepository;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import com.dropie.global.exception.custom.ProductNotFoundException;
import com.dropie.global.s3.S3Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AdminProductServiceTest {

    @InjectMocks
    private AdminProductService adminProductService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private S3Service s3Service;

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
    @DisplayName("상품 전체 목록 조회 성공 — JOIN FETCH로 조회하고 AdminProductResponse 리스트 반환")
    void 상품_전체목록_조회_성공() {
        // given
        // product는 setUp()에서 event와 연결되어 있음 — brandName 조회 가능
        given(productRepository.findAllWithEvent()).willReturn(List.of(product));

        // when
        List<AdminProductResponse> result = adminProductService.getProducts();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("초코두바이도넛");
        assertThat(result.get(0).getDescription()).isEqualTo("상품 설명");
        assertThat(result.get(0).getBrandName()).isEqualTo("노티드");
        assertThat(result.get(0).getPrice()).isEqualTo(5500);
        assertThat(result.get(0).getStock()).isEqualTo(100);
        // findAllWithEvent() 가 호출됐는지 확인 — findAll()이 아닌 JOIN FETCH 전용 쿼리를 써야 함
        then(productRepository).should().findAllWithEvent();
    }

    @Test
    @DisplayName("상품 전체 목록 조회 성공 — 상품이 없으면 빈 리스트 반환")
    void 상품_전체목록_조회_빈목록() {
        // given
        given(productRepository.findAllWithEvent()).willReturn(List.of());

        // when
        List<AdminProductResponse> result = adminProductService.getProducts();

        // then
        assertThat(result).isEmpty();
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

    @Test
    @DisplayName("상품 삭제 성공 — 상품 이미지 S3에서 삭제됨")
    void 상품_삭제_성공_이미지_삭제() {
        // given
        Product product = mock(Product.class);
        given(product.getImageUrl()).willReturn("https://s3.amazonaws.com/images/product.jpg");
        given(productRepository.findById(1L)).willReturn(Optional.of(product));

        // when
        adminProductService.deleteProduct(1L);

        // then — S3 삭제 1번, DB 삭제 1번
        then(s3Service).should().deleteImage("https://s3.amazonaws.com/images/product.jpg");
        then(productRepository).should().delete(product);
    }

    @Test
    @DisplayName("상품 삭제 실패 — 존재하지 않는 상품 삭제 시 ProductNotFoundException 발생")
    void 상품_삭제_실패_상품_없음() {
        // given
        given(productRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminProductService.deleteProduct(999L))
                .isInstanceOf(ProductNotFoundException.class);

        // S3 삭제가 호출되지 않아야 함 — 상품을 찾지 못했으니 이미지도 삭제 불가
        then(s3Service).should(never()).deleteImage(anyString());
    }
}
