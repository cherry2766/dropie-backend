package com.dropie.domain.event.service;

import com.dropie.domain.event.dto.response.EventDetailResponse;
import com.dropie.domain.event.dto.response.EventListResponse;
import com.dropie.domain.event.entity.Event;
import com.dropie.domain.event.entity.EventStatus;
import com.dropie.domain.event.repository.EventRepository;
import com.dropie.domain.product.entity.Product;
import com.dropie.domain.product.repository.ProductRepository;
import com.dropie.global.common.PageResponse;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @InjectMocks
    private EventService eventService;  // 테스트 대상 — Mock들이 자동 주입됨

    @Mock
    private EventRepository eventRepository;  // 실제 DB 대신 Mock으로 대체

    @Mock
    private ProductRepository productRepository;  // 실제 DB 대신 Mock으로 대체

    private Event event;
    private Product product;

    @BeforeEach
    void setUp() {
        // 각 테스트 실행 전마다 픽스처 초기화
        event = Event.builder()
                .brandName("노티드")
                .description("브랜드 설명")
                .thumbnailImageUrl("https://thumb.jpg")
                .imageUrl("https://image.jpg")
                .startAt(LocalDateTime.of(2026, 4, 1, 20, 0))
                .endAt(LocalDateTime.of(2026, 4, 1, 22, 0))
                .status(EventStatus.OPEN)
                .build();

        product = Product.builder()
                .event(event)
                .name("초코두바이도넛")
                .imageUrl("https://product.jpg")
                .description("상품 설명")
                .price(5500)
                .stock(30)
                .build();
    }

    @Test
    @DisplayName("이벤트 목록 조회 성공 - 페이지네이션 결과 반환")
    void 이벤트_목록_조회_성공() {
        // given
        // PageImpl: 실제 Page 구현체 — content, pageable, total 세 가지 필요
        // PageRequest.of(0, 6): API는 1-based지만 서비스에서 -1 해서 넘기므로 여기선 0-based
        Page<Event> eventPage = new PageImpl<>(
                List.of(event),
                PageRequest.of(0, 6),
                1  // totalElements
        );
        given(eventRepository.findAll(any(PageRequest.class))).willReturn(eventPage);

        // when
        PageResponse<EventListResponse> result = eventService.getEvents(1, 6);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getBrandName()).isEqualTo("노티드");
        assertThat(result.getPage()).isEqualTo(1);  // 1-based로 반환되는지 확인
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("이벤트 상세 조회 성공 - 이벤트 정보 + 상품 목록 반환")
    void 이벤트_상세_조회_성공() {
        // given
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));

        Page<Product> productPage = new PageImpl<>(
                List.of(product),
                PageRequest.of(0, 5),
                1
        );
        // findByEvent는 Event 객체와 Pageable 두 개를 인자로 받음
        given(productRepository.findByEvent(eq(event), any(PageRequest.class)))
                .willReturn(productPage);

        // when
        EventDetailResponse result = eventService.getEventDetail(1L, 1, 5);

        // then
        assertThat(result.getBrandName()).isEqualTo("노티드");
        assertThat(result.getDescription()).isEqualTo("브랜드 설명");
        assertThat(result.getProducts().getContent()).hasSize(1);
        assertThat(result.getProducts().getContent().get(0).getName()).isEqualTo("초코두바이도넛");
        assertThat(result.getProducts().getContent().get(0).getDescription()).isEqualTo("상품 설명");
    }

    @Test
    @DisplayName("이벤트 상세 조회 실패 - 없는 이벤트 404")
    void 이벤트_상세_조회_없는이벤트_예외() {
        // given
        given(eventRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> eventService.getEventDetail(999L, 1, 5))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVENT_NOT_FOUND);
    }
}