package com.dropie.domain.event.service;

import com.dropie.domain.event.entity.Event;
import com.dropie.domain.event.entity.EventStatus;
import com.dropie.domain.event.dto.request.CreateEventRequest;
import com.dropie.domain.event.dto.request.UpdateEventRequest;
import com.dropie.domain.event.dto.request.UpdateEventStatusRequest;
import com.dropie.domain.event.dto.response.AdminEventResponse;
import com.dropie.domain.event.dto.response.EventCreateResponse;
import com.dropie.domain.event.dto.response.EventStatusResponse;
import com.dropie.domain.event.repository.EventRepository;
import com.dropie.domain.product.entity.Product;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
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
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AdminEventServiceTest {

    @InjectMocks
    private AdminEventService adminEventService;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private S3Service s3Service;

    private Event upcomingEvent;
    private Event openEvent;
    private Event finishedEvent;

    @BeforeEach
    void setUp() {
        upcomingEvent = Event.builder()
                .brandName("노티드")
                .description("설명")
                .thumbnailImageUrl("https://thumb.jpg")
                .imageUrl("https://image.jpg")
                .startAt(LocalDateTime.of(2026, 4, 1, 20, 0))
                .endAt(LocalDateTime.of(2026, 4, 1, 22, 0))
                .status(EventStatus.UPCOMING)
                .build();

        openEvent = Event.builder()
                .brandName("노티드")
                .description("설명")
                .thumbnailImageUrl("https://thumb.jpg")
                .imageUrl("https://image.jpg")
                .startAt(LocalDateTime.of(2026, 4, 1, 20, 0))
                .endAt(LocalDateTime.of(2026, 4, 1, 22, 0))
                .status(EventStatus.OPEN)
                .build();

        finishedEvent = Event.builder()
                .brandName("노티드")
                .description("설명")
                .thumbnailImageUrl("https://thumb.jpg")
                .imageUrl("https://image.jpg")
                .startAt(LocalDateTime.of(2026, 4, 1, 20, 0))
                .endAt(LocalDateTime.of(2026, 4, 1, 22, 0))
                .status(EventStatus.FINISHED)
                .build();
    }

    @Test
    @DisplayName("이벤트 등록 성공 - status는 항상 UPCOMING")
    void 이벤트_등록_성공() {
        // given
        CreateEventRequest request = new CreateEventRequest(
                "노티드", "설명", "https://thumb.jpg", "https://image.jpg",
                LocalDateTime.of(2026, 4, 1, 20, 0),
                LocalDateTime.of(2026, 4, 1, 22, 0)
        );

        given(eventRepository.save(any())).willReturn(upcomingEvent);

        // when
        EventCreateResponse response = adminEventService.createEvent(request);

        // then
        // 요청과 무관하게 등록 직후는 UPCOMING이어야 함
        assertThat(response.getStatus()).isEqualTo(EventStatus.UPCOMING);
        then(eventRepository).should().save(any(Event.class));
    }

    @Test
    @DisplayName("이벤트 수정 성공 - null 필드는 기존값 유지")
    void 이벤트_수정_성공() {
        // given
        // description만 수정, 나머지는 null
        UpdateEventRequest request = UpdateEventRequest.builder()
                .description("수정된 설명")
                .build();

        given(eventRepository.findById(1L)).willReturn(Optional.of(upcomingEvent));

        // when
        adminEventService.updateEvent(1L, request);

        // then
        // dirty checking으로 반영 — description이 수정됐는지 확인
        assertThat(upcomingEvent.getDescription()).isEqualTo("수정된 설명");
        // imageUrl 등 null로 보낸 필드는 기존값 유지
        assertThat(upcomingEvent.getImageUrl()).isEqualTo("https://image.jpg");
    }

    @Test
    @DisplayName("이벤트 수정 실패 - 없는 이벤트")
    void 이벤트_수정_없는이벤트_예외() {
        // given
        given(eventRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                adminEventService.updateEvent(999L, UpdateEventRequest.builder().build()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVENT_NOT_FOUND);
    }

    @Test
    @DisplayName("이벤트 상태 변경 성공 - UPCOMING → OPEN")
    void 이벤트_상태변경_성공_UPCOMING_to_OPEN() {
        // given
        given(eventRepository.findById(1L)).willReturn(Optional.of(upcomingEvent));
        UpdateEventStatusRequest request = new UpdateEventStatusRequest(EventStatus.OPEN);

        // when
        EventStatusResponse response = adminEventService.changeEventStatus(1L, request);

        // then
        assertThat(response.getStatus()).isEqualTo(EventStatus.OPEN);
        assertThat(upcomingEvent.getStatus()).isEqualTo(EventStatus.OPEN);
    }

    @Test
    @DisplayName("이벤트 상태 변경 실패 - FINISHED → OPEN 허용 불가")
    void 이벤트_상태변경_허용불가전환_FINISHED_to_OPEN_예외() {
        // given
        given(eventRepository.findById(1L)).willReturn(Optional.of(finishedEvent));
        // FINISHED는 어떤 상태로도 전환 불가
        UpdateEventStatusRequest request = new UpdateEventStatusRequest(EventStatus.OPEN);

        // when & then
        assertThatThrownBy(() ->
                adminEventService.changeEventStatus(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    @DisplayName("이벤트 상태 변경 성공 - OPEN → CLOSED")
    void 이벤트_상태변경_성공_OPEN_to_CLOSED() {
        // given
        given(eventRepository.findById(1L)).willReturn(Optional.of(openEvent));
        UpdateEventStatusRequest request = new UpdateEventStatusRequest(EventStatus.CLOSED);

        // when
        EventStatusResponse response = adminEventService.changeEventStatus(1L, request);

        // then
        assertThat(response.getStatus()).isEqualTo(EventStatus.CLOSED);
        assertThat(openEvent.getStatus()).isEqualTo(EventStatus.CLOSED);
    }

    @Test
    @DisplayName("이벤트 상태 변경 실패 - OPEN → UPCOMING 허용 불가")
    void 이벤트_상태변경_허용불가전환_OPEN_to_UPCOMING_예외() {
        // given
        given(eventRepository.findById(1L)).willReturn(Optional.of(openEvent));
        UpdateEventStatusRequest request = new UpdateEventStatusRequest(EventStatus.UPCOMING);

        // when & then
        assertThatThrownBy(() ->
                adminEventService.changeEventStatus(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    @DisplayName("이벤트 전체 목록 조회 성공 — 이벤트 수만큼 AdminEventResponse 반환")
    void 이벤트_전체목록_조회_성공() {
        // given
        given(eventRepository.findAll()).willReturn(List.of(upcomingEvent, openEvent));

        // when
        List<AdminEventResponse> result = adminEventService.getEvents();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getBrandName()).isEqualTo("노티드");
        assertThat(result.get(0).getDescription()).isEqualTo("설명");
        assertThat(result.get(0).getThumbnailImageUrl()).isEqualTo("https://thumb.jpg");
        assertThat(result.get(0).getImageUrl()).isEqualTo("https://image.jpg");
        assertThat(result.get(0).getStatus()).isEqualTo(EventStatus.UPCOMING);
        assertThat(result.get(1).getStatus()).isEqualTo(EventStatus.OPEN);
        then(eventRepository).should().findAll();
    }

    @Test
    @DisplayName("이벤트 전체 목록 조회 성공 — 이벤트가 없으면 빈 리스트 반환")
    void 이벤트_전체목록_조회_빈목록() {
        // given
        given(eventRepository.findAll()).willReturn(List.of());

        // when
        List<AdminEventResponse> result = adminEventService.getEvents();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("이벤트 삭제 성공")
    void 이벤트_삭제_성공() {
        // given
        given(eventRepository.findById(1L)).willReturn(Optional.of(upcomingEvent));

        // when
        adminEventService.deleteEvent(1L);

        // then
        then(eventRepository).should().delete(upcomingEvent);
    }
    @Test
    @DisplayName("이벤트 삭제 실패 - 없는 이벤트")
    void 이벤트_삭제_없는이벤트_예외() {
        // given
        given(eventRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminEventService.deleteEvent(999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVENT_NOT_FOUND);
    }

    @Test
    @DisplayName("이벤트 삭제 성공 — 상품 이미지와 이벤트 이미지 S3에서 삭제됨")
    void 이벤트_삭제_성공_이미지_삭제() {
        // given
        Product product1 = mock(Product.class);
        Product product2 = mock(Product.class);
        given(product1.getImageUrl()).willReturn("https://s3.amazonaws.com/images/p1.jpg");
        given(product2.getImageUrl()).willReturn("https://s3.amazonaws.com/images/p2.jpg");

        Event event = mock(Event.class);
        given(event.getProducts()).willReturn(List.of(product1, product2));
        given(event.getThumbnailImageUrl()).willReturn("https://s3.amazonaws.com/images/thumb.jpg");
        given(event.getImageUrl()).willReturn("https://s3.amazonaws.com/images/event.jpg");
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));

        // when
        adminEventService.deleteEvent(1L);

        // then — 상품 이미지 2개 + 이벤트 이미지 2개 = 총 4번 S3 삭제 호출
        then(s3Service).should(times(4)).deleteImage(anyString());
        then(eventRepository).should().delete(event);
    }

    @Test
    @DisplayName("이벤트 삭제 성공 — 상품이 없는 경우 이벤트 이미지만 S3에서 삭제됨")
    void 이벤트_삭제_성공_상품_없음() {
        // given
        Event event = mock(Event.class);
        given(event.getProducts()).willReturn(List.of()); // 상품 없음
        given(event.getThumbnailImageUrl()).willReturn("https://s3.amazonaws.com/images/thumb.jpg");
        given(event.getImageUrl()).willReturn("https://s3.amazonaws.com/images/event.jpg");
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));

        // when
        adminEventService.deleteEvent(1L);

        // then — 이벤트 이미지 2개만 삭제
        then(s3Service).should(times(2)).deleteImage(anyString());
        then(eventRepository).should().delete(event);
    }

}
