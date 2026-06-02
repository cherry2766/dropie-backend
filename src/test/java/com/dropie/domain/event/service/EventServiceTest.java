package com.dropie.domain.event.service;

import com.dropie.domain.event.dto.response.EventDetailResponse;
import com.dropie.domain.event.dto.response.EventListResponse;
import com.dropie.domain.event.dto.response.LineupRoundResponse;
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
        // EventStatusCalculator가 derived status를 계산하므로 시간 기반 분기에 걸리지 않도록
        // 현재 시각이 startAt~endAt 사이에 들어오게 세팅 (DB OPEN과 derived OPEN이 일치)
        event = Event.builder()
                .brandName("노티드")
                .description("브랜드 설명")
                .thumbnailImageUrl("https://thumb.jpg")
                .imageUrl("https://image.jpg")
                .startAt(LocalDateTime.now().minusHours(1))
                .endAt(LocalDateTime.now().plusHours(1))
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
        // status=null이면 전체 조회 경로 실행
        PageResponse<EventListResponse> result = eventService.getEvents(1, 6, null);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getBrandName()).isEqualTo("노티드");
        assertThat(result.getPage()).isEqualTo(1);  // 1-based로 반환되는지 확인
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("이벤트 목록 조회 성공 - status 필터 적용 시 findByStatus가 호출되고 해당 상태만 반환")
    void 이벤트_목록_상태_필터_조회_성공() {
        // given
        Page<Event> eventPage = new PageImpl<>(
                List.of(event),
                PageRequest.of(0, 6),
                1
        );
        // status=OPEN으로 필터링하면 findByStatus가 호출되어야 함
        given(eventRepository.findByStatus(eq(EventStatus.OPEN), any(PageRequest.class))).willReturn(eventPage);
        // 재고가 남아있는 상태로 가정 → allSoldOut=false → derived가 SOLD_OUT으로 덮이지 않음
        given(productRepository.existsByEventAndStockGreaterThan(any(), eq(0))).willReturn(true);

        // when
        PageResponse<EventListResponse> result = eventService.getEvents(1, 6, EventStatus.OPEN);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(EventStatus.OPEN);
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

    // ===================== getLineup =====================

    @Test
    @DisplayName("라인업 조회 성공 — startAt/endAt이 같은 이벤트가 같은 차수로 묶임")
    void 라인업_조회_성공_같은시간대_묶음() {
        // given
        // 1차는 과거 시간대(FINISHED), 2차는 현재 판매 중(OPEN)으로 derived status가 일치하도록 설정
        LocalDateTime start1 = LocalDateTime.now().minusDays(60);
        LocalDateTime end1   = LocalDateTime.now().minusDays(30);
        LocalDateTime start2 = LocalDateTime.now().minusHours(1);
        LocalDateTime end2   = LocalDateTime.now().plusHours(1);

        // 1차: FINISHED 2개, 2차: OPEN 2개 — 같은 startAt/endAt끼리 묶여야 함
        Event e1 = Event.builder().brandName("솔트버터").startAt(start1).endAt(end1).status(EventStatus.FINISHED).build();
        Event e2 = Event.builder().brandName("노아케이크").startAt(start1).endAt(end1).status(EventStatus.FINISHED).build();
        Event e3 = Event.builder().brandName("밀담제과").startAt(start2).endAt(end2).status(EventStatus.OPEN).build();
        Event e4 = Event.builder().brandName("도넛클럽").startAt(start2).endAt(end2).status(EventStatus.OPEN).build();

        given(eventRepository.findAllByOrderByStartAtAsc()).willReturn(List.of(e1, e2, e3, e4));
        // 재고가 남아있는 상태로 가정 → 2차의 OPEN이 SOLD_OUT으로 덮이지 않음
        given(productRepository.existsByEventAndStockGreaterThan(any(), eq(0))).willReturn(true);

        // when
        List<LineupRoundResponse> result = eventService.getLineup();

        // then
        assertThat(result).hasSize(2);

        // 1차: FINISHED, 브랜드 2개
        assertThat(result.get(0).getRound()).isEqualTo(1);
        assertThat(result.get(0).getStatus()).isEqualTo("FINISHED");
        assertThat(result.get(0).getBrands()).containsExactly("솔트버터", "노아케이크");

        // 2차: OPEN, 브랜드 2개
        assertThat(result.get(1).getRound()).isEqualTo(2);
        assertThat(result.get(1).getStatus()).isEqualTo("OPEN");
        assertThat(result.get(1).getBrands()).containsExactly("밀담제과", "도넛클럽");
    }

    @Test
    @DisplayName("라인업 조회 성공 — 이벤트가 없으면 빈 리스트 반환")
    void 라인업_조회_이벤트없음_빈리스트() {
        // given
        given(eventRepository.findAllByOrderByStartAtAsc()).willReturn(List.of());

        // when
        List<LineupRoundResponse> result = eventService.getLineup();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("라인업 조회 성공 — 차수 번호가 startAt 오름차순으로 1부터 순서대로 부여됨")
    void 라인업_조회_차수번호_순서확인() {
        // given
        LocalDateTime start1 = LocalDateTime.of(2026, 1, 10, 11, 0);
        LocalDateTime start2 = LocalDateTime.of(2026, 4, 1, 11, 0);
        LocalDateTime start3 = LocalDateTime.of(2027, 1, 10, 11, 0);
        LocalDateTime end1   = LocalDateTime.of(2026, 3, 31, 23, 59);
        LocalDateTime end2   = LocalDateTime.of(2026, 12, 31, 23, 59);
        LocalDateTime end3   = LocalDateTime.of(2027, 3, 31, 23, 59);

        Event e1 = Event.builder().brandName("1차브랜드").startAt(start1).endAt(end1).status(EventStatus.FINISHED).build();
        Event e2 = Event.builder().brandName("2차브랜드").startAt(start2).endAt(end2).status(EventStatus.OPEN).build();
        Event e3 = Event.builder().brandName("3차브랜드").startAt(start3).endAt(end3).status(EventStatus.UPCOMING).build();

        given(eventRepository.findAllByOrderByStartAtAsc()).willReturn(List.of(e1, e2, e3));

        // when
        List<LineupRoundResponse> result = eventService.getLineup();

        // then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getRound()).isEqualTo(1);
        assertThat(result.get(1).getRound()).isEqualTo(2);
        assertThat(result.get(2).getRound()).isEqualTo(3);
    }
}