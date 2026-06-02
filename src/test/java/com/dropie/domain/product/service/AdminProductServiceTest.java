package com.dropie.domain.product.service;

import com.dropie.domain.event.entity.Event;
import com.dropie.domain.event.entity.EventStatus;
import com.dropie.domain.event.repository.EventRepository;
import com.dropie.domain.product.dto.request.CreateProductRequest;
import com.dropie.domain.product.dto.request.UpdateProductRequest;
import com.dropie.domain.product.dto.request.UpdateProductStockRequest;
import com.dropie.domain.product.dto.response.AdminProductResponse;
import com.dropie.domain.product.dto.response.ProductCreateResponse;
import com.dropie.domain.product.dto.response.ProductStockResponse;
import com.dropie.domain.product.entity.Product;
import com.dropie.domain.product.repository.ProductRepository;
import com.dropie.domain.product.repository.ProductTagRepository;
import com.dropie.domain.tag.entity.Tag;
import com.dropie.domain.tag.repository.TagRepository;
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
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AdminProductServiceTest {

    @InjectMocks
    private AdminProductService adminProductService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private EventRepository eventRepository;

    // 상품-태그 연결 (find-or-create 패턴)에서 사용
    @Mock
    private TagRepository tagRepository;

    @Mock
    private ProductTagRepository productTagRepository;

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

    // ===== 상품 목록 조회 =====

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

    // ===== 상품 등록 =====

    @Test
    @DisplayName("상품 등록 성공 — tagNames null이면 태그 관련 의존은 호출되지 않음")
    void 상품_등록_성공_태그_null() {
        // given
        // 7번째 인자 tagNames=null
        CreateProductRequest request = new CreateProductRequest(
                1L, "초코두바이도넛", "https://product.jpg", "설명", 5500, 100, null);

        given(eventRepository.findById(1L)).willReturn(Optional.of(event));
        given(productRepository.save(any())).willReturn(product);

        // when
        ProductCreateResponse response = adminProductService.createProduct(request);

        // then
        assertThat(response.getName()).isEqualTo("초코두바이도넛");
        assertThat(response.getStock()).isEqualTo(100);
        then(productRepository).should().save(any(Product.class));
        // 태그 입력 자체가 없으면 tagRepository / productTagRepository 둘 다 손대지 않아야 함
        then(tagRepository).shouldHaveNoInteractions();
        then(productTagRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("상품 등록 성공 — tagNames가 빈 배열이어도 태그 관련 의존 호출되지 않음")
    void 상품_등록_성공_태그_빈배열() {
        // given
        // 빈 배열도 null과 마찬가지로 attachTags 호출 안 됨
        CreateProductRequest request = new CreateProductRequest(
                1L, "초코두바이도넛", "https://product.jpg", "설명", 5500, 100, List.of());

        given(eventRepository.findById(1L)).willReturn(Optional.of(event));
        given(productRepository.save(any())).willReturn(product);

        // when
        adminProductService.createProduct(request);

        // then
        then(tagRepository).shouldHaveNoInteractions();
        then(productTagRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("상품 등록 — tagNames 있을 때 find-or-create: 기존 태그는 재사용, 신규 태그는 onboardingExposed=false로 자동 생성")
    void 상품_등록_태그_findOrCreate() {
        // given
        // "달콤한"은 DB에 이미 있음 / "초콜릿", "마카롱"은 신규 → 자동 생성되어야 함
        CreateProductRequest request = new CreateProductRequest(
                1L, "라즈베리마카롱", "https://product.jpg", "설명", 4000, 50,
                List.of("달콤한", "초콜릿", "마카롱"));

        Tag existingTag = Tag.builder().id(1L).name("달콤한").onboardingExposed(true).build();
        Tag newChocolate = Tag.builder().id(11L).name("초콜릿").onboardingExposed(false).build();
        Tag newMacaron = Tag.builder().id(12L).name("마카롱").onboardingExposed(false).build();

        given(eventRepository.findById(1L)).willReturn(Optional.of(event));
        given(productRepository.save(any())).willReturn(product);
        // 기존 태그는 findByName이 Optional 채워서 반환
        given(tagRepository.findByName("달콤한")).willReturn(Optional.of(existingTag));
        // 신규 태그는 빈 Optional → save 경로로 빠짐
        given(tagRepository.findByName("초콜릿")).willReturn(Optional.empty());
        given(tagRepository.findByName("마카롱")).willReturn(Optional.empty());
        // save가 두 번 호출됨 — 호출 순서대로 newChocolate, newMacaron 반환
        given(tagRepository.save(any(Tag.class))).willReturn(newChocolate, newMacaron);

        // when
        adminProductService.createProduct(request);

        // then
        // 3개 이름 각각 findByName 1번씩 호출
        then(tagRepository).should().findByName("달콤한");
        then(tagRepository).should().findByName("초콜릿");
        then(tagRepository).should().findByName("마카롱");
        // 신규 태그 2개만 save 호출 (기존은 재사용이라 save 안 됨)
        then(tagRepository).should(times(2)).save(any(Tag.class));
        // ProductTag 3개 saveAll 1번 호출 (3개 태그 모두 product에 연결)
        then(productTagRepository).should().saveAll(any());
    }

    @Test
    @DisplayName("상품 등록 — tagNames 정규화: 앞뒤 공백 trim + 빈 문자열 제거 + 중복 제거")
    void 상품_등록_태그_정규화() {
        // given
        // "  달콤한  " (앞뒤 공백) + "" (빈 문자열) + "달콤한" (중복) + "초콜릿"
        // → 정규화 후 ["달콤한", "초콜릿"] 2개만 처리되어야 함
        CreateProductRequest request = new CreateProductRequest(
                1L, "도넛", null, null, 3000, 30,
                List.of("  달콤한  ", "", "달콤한", "초콜릿"));

        Tag tag1 = Tag.builder().id(1L).name("달콤한").build();
        Tag tag2 = Tag.builder().id(2L).name("초콜릿").build();

        given(eventRepository.findById(1L)).willReturn(Optional.of(event));
        given(productRepository.save(any())).willReturn(product);
        given(tagRepository.findByName("달콤한")).willReturn(Optional.of(tag1));
        given(tagRepository.findByName("초콜릿")).willReturn(Optional.of(tag2));

        // when
        adminProductService.createProduct(request);

        // then
        // 정규화 결과 정확히 2번만 findByName 호출 (4번이 아님)
        then(tagRepository).should(times(2)).findByName(anyString());
        // trim된 정확한 값으로 호출 — "  달콤한  " 그대로면 매칭이 빗나감
        then(tagRepository).should().findByName("달콤한");
        then(tagRepository).should().findByName("초콜릿");
    }

    @Test
    @DisplayName("상품 등록 실패 - 없는 이벤트")
    void 상품_등록_없는이벤트_예외() {
        // given
        CreateProductRequest request = new CreateProductRequest(999L, "도넛", null, null, 5500, 100, null);

        given(eventRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminProductService.createProduct(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVENT_NOT_FOUND);

        // 이벤트 검증 실패 → 이후 단계(상품 저장 / 태그 처리) 모두 호출되면 안 됨
        then(productRepository).should(never()).save(any());
        then(tagRepository).shouldHaveNoInteractions();
        then(productTagRepository).shouldHaveNoInteractions();
    }

    // ===== 상품 수정 (필드) =====

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

    // ===== 상품 수정 (태그 PATCH 규약) =====

    @Test
    @DisplayName("상품 수정 — tagNames null(미포함)이면 태그 변경 없음 — ProductTag 손대지 않음")
    void 상품_수정_태그_변경없음() {
        // given
        // tagNames 명시 안 함 → null → "변경 없음" 의미
        UpdateProductRequest request = UpdateProductRequest.builder()
                .price(6000)
                .build();

        given(productRepository.findById(1L)).willReturn(Optional.of(product));

        // when
        adminProductService.updateProduct(1L, request);

        // then
        // null이면 PATCH 규약상 "기존 유지" → ProductTag 어떤 메서드도 호출 안 됨
        then(productTagRepository).shouldHaveNoInteractions();
        then(tagRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("상품 수정 — tagNames 빈 배열이면 모든 태그 제거 — deleteAllByProduct + flush 호출, 신규 attach 없음")
    void 상품_수정_태그_모두제거() {
        // given
        UpdateProductRequest request = UpdateProductRequest.builder()
                .tagNames(List.of())
                .build();

        given(productRepository.findById(1L)).willReturn(Optional.of(product));

        // when
        adminProductService.updateProduct(1L, request);

        // then
        // 빈 배열은 "모두 제거" 의미 → delete + flush 까지만, 새로 붙이는 단계는 안 거침
        then(productTagRepository).should().deleteAllByProduct(product);
        // delete 후 flush를 명시적으로 호출 — 같은 트랜잭션에서 다음 INSERT가 unique 제약에 안 걸리게
        then(productTagRepository).should().flush();
        // 빈 배열이라 saveAll(new ProductTag) 단계는 일어나면 안 됨
        then(productTagRepository).should(never()).saveAll(any());
        // tagRepository(findByName, save)도 손대지 않음
        then(tagRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("상품 수정 — tagNames 값 있음이면 기존 다 제거 후 새 목록으로 replace")
    void 상품_수정_태그_replace() {
        // given
        UpdateProductRequest request = UpdateProductRequest.builder()
                .tagNames(List.of("달콤한", "초콜릿"))
                .build();

        Tag tag1 = Tag.builder().id(1L).name("달콤한").build();
        Tag tag2 = Tag.builder().id(2L).name("초콜릿").build();

        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(tagRepository.findByName("달콤한")).willReturn(Optional.of(tag1));
        given(tagRepository.findByName("초콜릿")).willReturn(Optional.of(tag2));

        // when
        adminProductService.updateProduct(1L, request);

        // then
        // 기존 태그 전부 제거 → flush → 새 태그 attach 순서 모두 호출
        then(productTagRepository).should().deleteAllByProduct(product);
        then(productTagRepository).should().flush();
        then(productTagRepository).should().saveAll(any());
        // 새 목록의 두 이름 각각 findByName으로 조회됨 (find-or-create 진입)
        then(tagRepository).should().findByName("달콤한");
        then(tagRepository).should().findByName("초콜릿");
    }

    // ===== 재고 수정 =====

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

    // ===== 상품 삭제 =====

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
