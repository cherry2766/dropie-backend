package com.dropie.domain.order.service;

import com.dropie.domain.event.entity.Event;
import com.dropie.domain.event.entity.EventStatus;
import com.dropie.domain.order.dto.request.CreateOrderRequest;
import com.dropie.domain.order.dto.response.OrderCancelResponse;
import com.dropie.domain.order.dto.response.OrderCreateResponse;
import com.dropie.domain.order.dto.response.OrderDetailResponse;
import com.dropie.domain.order.dto.response.OrderResponse;
import com.dropie.domain.order.entity.Order;
import com.dropie.domain.order.entity.OrderItem;
import com.dropie.domain.order.entity.OrderStatus;
import com.dropie.domain.order.repository.OrderRepository;
import com.dropie.domain.product.entity.Product;
import com.dropie.domain.product.repository.ProductRepository;
import com.dropie.domain.user.entity.Role;
import com.dropie.domain.user.entity.User;
import com.dropie.global.common.PageResponse;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import com.dropie.global.exception.custom.OrderNotFoundException;
import com.dropie.global.exception.custom.OutOfStockException;
import com.dropie.global.exception.custom.ProductNotFoundException;
import com.dropie.global.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

// Spring Context 없이 Mockito만으로 OrderService 단위 테스트
// DB, 네트워크 등 외부 의존성 없이 순수하게 비즈니스 로직만 검증
@ExtendWith(MockitoExtension.class)
// @BeforeEach에서 세팅한 stub이 일부 테스트에서 호출되지 않아도 오류 내지 않도록 설정
// (예: 예외가 중간에 터져서 userDetails.getUser()까지 코드가 도달하지 못하는 케이스)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

    // 테스트 대상 — 아래 @Mock 객체들이 자동 주입됨
    @InjectMocks
    private OrderService orderService;

    // 실제 DB 대신 가짜 객체로 대체 — 직접 제어 가능
    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    // OrderService.createOrder()가 redisTemplate.opsForValue().set(...)을 호출함
    // RETURNS_DEEP_STUBS: opsForValue() 같은 체이닝 호출도 자동으로 mock 반환 → NPE 방지
    // (실제 try-catch로 감싸져 있어 NPE가 발생해도 테스트는 통과하지만, 로그 오염 방지를 위해 모킹)
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private StringRedisTemplate redisTemplate;

    // 각 테스트에서 공통으로 쓰는 객체들
    private User user;
    private CustomUserDetails userDetails;
    private Event openEvent;

    // 각 테스트 실행 전 공통 데이터 세팅
    @BeforeEach
    void setUp() {
        // 로그인한 유저 — id를 직접 지정해야 소유자 검증 테스트가 가능
        user = User.builder()
                .id(1L)
                .email("test@test.com")
                .password("password")
                .nickname("테스터")
                .role(Role.USER)
                .build();

        // CustomUserDetails는 인터페이스라 mock으로 생성
        // getUser() 호출 시 위에서 만든 user 반환하도록 설정
        userDetails = mock(CustomUserDetails.class);
        given(userDetails.getUser()).willReturn(user);

        // 현재 판매 중인 정상 이벤트
        // startAt을 1시간 전, endAt을 1시간 후로 설정해서 "지금 판매 중" 상태를 재현
        openEvent = Event.builder()
                .brandName("테스트브랜드")
                .status(EventStatus.OPEN)
                .startAt(LocalDateTime.now().minusHours(1))
                .endAt(LocalDateTime.now().plusHours(1))
                .build();

        // 기본값: 재고가 남은 상품이 있음 → 이벤트 자동 CLOSED 미발생
        // 품절 자동 CLOSED 테스트에서만 false로 재정의
        given(productRepository.existsByEventAndStockGreaterThan(any(Event.class), eq(0)))
                .willReturn(true);
    }

    // createOrder — 주문 생성
    @Nested
    @DisplayName("createOrder")
    class CreateOrder {

        @Test
        @DisplayName("정상 주문 생성 — 단일 상품, totalPrice와 PENDING 상태 확인")
        void 정상_주문_생성_단일상품() {
            // given
            Product product = Product.builder()
                    .id(10L)
                    .name("크로와상")
                    .price(5000)
                    .stock(10)
                    .event(openEvent)
                    .build();

            given(productRepository.findByIdWithOptimisticLock(10L)).willReturn(Optional.of(product));
            // save()가 호출되면 넘어온 Order 그대로 반환 (DB가 없으니 저장된 척)
            given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));

            CreateOrderRequest request = buildRequest(List.of(buildItem(10L, 2)));

            // when
            OrderCreateResponse response = orderService.createOrder(request, userDetails);

            // then — 5000원 * 2개 = 10000원, 주문 직후는 항상 PENDING
            assertThat(response.getTotalPrice()).isEqualTo(10000);
            assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        @DisplayName("여러 상품 주문 — totalPrice가 모든 상품 금액의 합산인지 확인")
        void 정상_주문_생성_여러상품_총금액_합산() {
            // given
            // 상품A: 3000원 * 2개 = 6000
            // 상품B: 7000원 * 1개 = 7000
            // 합계: 13000
            Product productA = Product.builder().id(1L).name("상품A").price(3000).stock(10).event(openEvent).build();
            Product productB = Product.builder().id(2L).name("상품B").price(7000).stock(10).event(openEvent).build();

            given(productRepository.findByIdWithOptimisticLock(1L)).willReturn(Optional.of(productA));
            given(productRepository.findByIdWithOptimisticLock(2L)).willReturn(Optional.of(productB));
            given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));

            CreateOrderRequest request = buildRequest(List.of(
                    buildItem(1L, 2),
                    buildItem(2L, 1)
            ));

            // when
            orderService.createOrder(request, userDetails);

            // then — save에 넘어간 Order의 totalPrice가 13000인지 검증
            // argThat: "이 조건을 만족하는 객체가 넘어왔는지" 검사
            then(orderRepository).should().save(argThat(order ->
                    order.getTotalPrice() == 13000
            ));
        }

        @Test
        @DisplayName("주문 후 재고 차감 확인 — 주문 수량만큼 stock 감소")
        void 정상_주문_후_재고_차감() {
            // given — stock 10개에서 3개 주문 → 7개 남아야 함
            Product product = Product.builder().id(1L).name("상품").price(1000).stock(10).event(openEvent).build();

            given(productRepository.findByIdWithOptimisticLock(1L)).willReturn(Optional.of(product));
            given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            CreateOrderRequest request = buildRequest(List.of(buildItem(1L, 3)));

            // when
            orderService.createOrder(request, userDetails);

            // then — 실제 product 객체의 stock이 7로 줄었는지 확인
            assertThat(product.getStock()).isEqualTo(7);
        }

        @Test
        @DisplayName("동일 productId 중복 요청 — DUPLICATE_ORDER_ITEM 예외, DB 조회 없어야 함")
        void 중복_상품_주문_예외() {
            // given — 같은 productId(1L)가 두 번 들어온 경우
            // 이 경우 재고가 두 번 차감될 위험이 있으므로 사전에 막음
            CreateOrderRequest request = buildRequest(List.of(
                    buildItem(1L, 2),
                    buildItem(1L, 3)
            ));

            // when & then
            assertThatThrownBy(() -> orderService.createOrder(request, userDetails))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.DUPLICATE_ORDER_ITEM);

            // 중복 체크는 DB 조회 전에 막혀야 함 — productRepository가 아예 호출되면 안 됨
            then(productRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("존재하지 않는 상품 — ProductNotFoundException")
        void 존재하지_않는_상품_주문_예외() {
            // given — DB에 없는 상품 ID 요청
            given(productRepository.findByIdWithOptimisticLock(999L)).willReturn(Optional.empty());

            CreateOrderRequest request = buildRequest(List.of(buildItem(999L, 1)));

            // when & then
            assertThatThrownBy(() -> orderService.createOrder(request, userDetails))
                    .isInstanceOf(ProductNotFoundException.class);
        }

        @Test
        @DisplayName("이벤트 CLOSED 상태 — EVENT_ENDED 예외")
        void 이벤트_CLOSED_상태_주문_예외() {
            // given — 관리자가 일시 중단(CLOSED) 처리한 이벤트
            Event closedEvent = Event.builder()
                    .brandName("브랜드")
                    .status(EventStatus.CLOSED)
                    .startAt(LocalDateTime.now().minusHours(2))
                    .endAt(LocalDateTime.now().plusHours(1))
                    .build();

            Product product = Product.builder().id(1L).name("상품").price(1000).stock(10).event(closedEvent).build();
            given(productRepository.findByIdWithOptimisticLock(1L)).willReturn(Optional.of(product));

            CreateOrderRequest request = buildRequest(List.of(buildItem(1L, 1)));

            // when & then
            assertThatThrownBy(() -> orderService.createOrder(request, userDetails))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.EVENT_ENDED);
        }

        @Test
        @DisplayName("이벤트 FINISHED 상태 — EVENT_ENDED 예외")
        void 이벤트_FINISHED_상태_주문_예외() {
            // given — 판매가 완전히 종료된 이벤트
            Event finishedEvent = Event.builder()
                    .brandName("브랜드")
                    .status(EventStatus.FINISHED)
                    .startAt(LocalDateTime.now().minusHours(2))
                    .endAt(LocalDateTime.now().minusHours(1))
                    .build();

            Product product = Product.builder().id(1L).name("상품").price(1000).stock(10).event(finishedEvent).build();
            given(productRepository.findByIdWithOptimisticLock(1L)).willReturn(Optional.of(product));

            CreateOrderRequest request = buildRequest(List.of(buildItem(1L, 1)));

            // when & then
            assertThatThrownBy(() -> orderService.createOrder(request, userDetails))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.EVENT_ENDED);
        }

        @Test
        @DisplayName("이벤트 종료 시각 경과 — EVENT_ENDED 예외 (상태는 OPEN이지만 endAt이 지난 경우)")
        void 이벤트_종료시각_경과_주문_예외() {
            // given — 관리자가 OPEN으로 설정했지만 endAt이 이미 지난 상황
            // 상태(status)와 시간(endAt)을 둘 다 검증하는 이유:
            // 관리자가 상태 변경을 깜빡했거나, 자동 상태 전환이 없는 경우를 커버하기 위함
            Event expiredEvent = Event.builder()
                    .brandName("브랜드")
                    .status(EventStatus.OPEN)
                    .startAt(LocalDateTime.now().minusHours(2))
                    .endAt(LocalDateTime.now().minusMinutes(1)) // 1분 전에 이미 종료됨
                    .build();

            Product product = Product.builder().id(1L).name("상품").price(1000).stock(10).event(expiredEvent).build();
            given(productRepository.findByIdWithOptimisticLock(1L)).willReturn(Optional.of(product));

            CreateOrderRequest request = buildRequest(List.of(buildItem(1L, 1)));

            // when & then
            assertThatThrownBy(() -> orderService.createOrder(request, userDetails))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.EVENT_ENDED);
        }

        @Test
        @DisplayName("이벤트 UPCOMING 상태 — EVENT_NOT_STARTED 예외")
        void 이벤트_UPCOMING_상태_주문_예외() {
            // given — 아직 오픈 전인 이벤트
            Event upcomingEvent = Event.builder()
                    .brandName("브랜드")
                    .status(EventStatus.UPCOMING)
                    .startAt(LocalDateTime.now().plusHours(1))
                    .endAt(LocalDateTime.now().plusHours(2))
                    .build();

            Product product = Product.builder().id(1L).name("상품").price(1000).stock(10).event(upcomingEvent).build();
            given(productRepository.findByIdWithOptimisticLock(1L)).willReturn(Optional.of(product));

            CreateOrderRequest request = buildRequest(List.of(buildItem(1L, 1)));

            // when & then
            assertThatThrownBy(() -> orderService.createOrder(request, userDetails))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.EVENT_NOT_STARTED);
        }

        @Test
        @DisplayName("이벤트 시작 전 — EVENT_NOT_STARTED 예외 (상태는 OPEN이지만 startAt 이전인 경우)")
        void 이벤트_시작전_주문_예외() {
            // given — 관리자가 미리 OPEN으로 설정했지만 아직 startAt이 되지 않은 상황
            Event notStartedEvent = Event.builder()
                    .brandName("브랜드")
                    .status(EventStatus.OPEN)
                    .startAt(LocalDateTime.now().plusMinutes(10)) // 10분 후 시작
                    .endAt(LocalDateTime.now().plusHours(1))
                    .build();

            Product product = Product.builder().id(1L).name("상품").price(1000).stock(10).event(notStartedEvent).build();
            given(productRepository.findByIdWithOptimisticLock(1L)).willReturn(Optional.of(product));

            CreateOrderRequest request = buildRequest(List.of(buildItem(1L, 1)));

            // when & then
            assertThatThrownBy(() -> orderService.createOrder(request, userDetails))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.EVENT_NOT_STARTED);
        }

        @Test
        @DisplayName("재고 부족 — OutOfStockException (stock 2개인데 3개 주문)")
        void 재고_부족_주문_예외() {
            // given — 재고가 2개밖에 없는데 3개 주문 시도
            Product product = Product.builder().id(1L).name("상품").price(1000).stock(2).event(openEvent).build();
            given(productRepository.findByIdWithOptimisticLock(1L)).willReturn(Optional.of(product));

            CreateOrderRequest request = buildRequest(List.of(buildItem(1L, 3)));

            // when & then
            assertThatThrownBy(() -> orderService.createOrder(request, userDetails))
                    .isInstanceOf(OutOfStockException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.OUT_OF_STOCK);
        }

        @Test
        @DisplayName("주문 후 이벤트 모든 상품 품절 — 이벤트 자동 CLOSED")
        void 주문_후_모든상품_품절_이벤트_자동CLOSED() {
            // given
            // 재고가 딱 1개인 상품 — 1개 주문하면 재고 0이 됨
            Product product = Product.builder()
                    .id(1L)
                    .name("크로와상")
                    .price(5000)
                    .stock(1)
                    .event(openEvent)
                    .build();

            given(productRepository.findByIdWithOptimisticLock(1L)).willReturn(Optional.of(product));
            given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));

            // 재고 차감 후 이 이벤트에 재고 남은 상품이 없음 → 전체 품절
            // @BeforeEach의 기본값(true)을 false로 재정의
            given(productRepository.existsByEventAndStockGreaterThan(any(Event.class), eq(0)))
                    .willReturn(false);

            CreateOrderRequest request = buildRequest(List.of(buildItem(1L, 1)));

            // when
            orderService.createOrder(request, userDetails);

            // then — openEvent 상태가 CLOSED로 바뀌었는지 확인
            // event는 mock이 아닌 실제 객체라 상태를 직접 검증 가능
            assertThat(openEvent.getStatus()).isEqualTo(EventStatus.CLOSED);
        }

        @Test
        @DisplayName("주문 후 재고가 남아있으면 이벤트 상태 유지 — OPEN 그대로")
        void 주문_후_재고_남아있으면_이벤트_상태_유지() {
            // given
            // 재고 10개 중 2개만 주문 → 아직 8개 남음
            Product product = Product.builder()
                    .id(1L)
                    .name("크로와상")
                    .price(5000)
                    .stock(10)
                    .event(openEvent)
                    .build();

            given(productRepository.findByIdWithOptimisticLock(1L)).willReturn(Optional.of(product));
            given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));

            // @BeforeEach 기본값 true — 재고 있는 상품이 아직 있음
            CreateOrderRequest request = buildRequest(List.of(buildItem(1L, 2)));

            // when
            orderService.createOrder(request, userDetails);

            // then — 이벤트 상태는 OPEN 그대로
            assertThat(openEvent.getStatus()).isEqualTo(EventStatus.OPEN);
        }
    }

    // getMyOrders — 내 주문 목록 조회

    @Nested
    @DisplayName("getMyOrders")
    class GetMyOrders {

        @Test
        @DisplayName("정상 조회 — 페이지네이션 결과 반환")
        void 정상_주문목록_조회() {
            // given
            Order order = Order.builder()
                    .id(1L)
                    .user(user)
                    .orderNumber("ORD-20260414-000001")
                    .totalPrice(10000)
                    .status(OrderStatus.PENDING)
                    .build();

            // PageImpl: Spring의 Page 인터페이스 구현체 — 테스트에서 가짜 페이지 결과를 만들 때 사용
            // findByUserWithBrands: 브랜드명 필드 추가 이후 OrderItems + Product + Event까지 fetch join하는 쿼리
            given(orderRepository.findByUserWithBrands(eq(user), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 10), 1));

            // when
            PageResponse<OrderResponse> response = orderService.getMyOrders(userDetails, 1, 10);

            // then
            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getPage()).isEqualTo(1);
            assertThat(response.getSize()).isEqualTo(10);
        }

        @Test
        @DisplayName("주문 없음 — 빈 리스트 반환 (예외 없음)")
        void 주문_없는_유저_빈리스트_반환() {
            // given — 주문이 하나도 없는 유저
            given(orderRepository.findByUserWithBrands(eq(user), any(Pageable.class)))
                    .willReturn(Page.empty());

            // when
            PageResponse<OrderResponse> response = orderService.getMyOrders(userDetails, 1, 10);

            // then — 예외 없이 빈 리스트로 정상 응답해야 함
            assertThat(response.getContent()).isEmpty();
            assertThat(response.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("page 1-based → 0-based 변환 확인 — page=2 요청 시 pageNumber=1로 변환")
        void 페이지_번호_변환_확인() {
            // given
            given(orderRepository.findByUserWithBrands(eq(user), any(Pageable.class)))
                    .willReturn(Page.empty());

            // when — 사용자는 page=2를 요청 (1-based)
            orderService.getMyOrders(userDetails, 2, 10);

            // then — 내부적으로 DB에는 0-based로 변환되어 pageNumber=1이 전달되어야 함
            // API 스펙은 1-based이지만 JPA는 0-based라서 변환이 필요
            then(orderRepository).should().findByUserWithBrands(eq(user),
                    argThat(pageable -> pageable.getPageNumber() == 1));
        }

        @Test
        @DisplayName("최신순 정렬 확인 — createdAt DESC 정렬인지 검증")
        void 최신순_정렬_확인() {
            // given
            given(orderRepository.findByUserWithBrands(eq(user), any(Pageable.class)))
                    .willReturn(Page.empty());

            // when
            orderService.getMyOrders(userDetails, 1, 10);

            // then — 최신 주문이 먼저 오도록 createdAt 내림차순 정렬인지 확인
            then(orderRepository).should().findByUserWithBrands(eq(user),
                    argThat(pageable -> {
                        var sort = pageable.getSort().getOrderFor("createdAt");
                        return sort != null && sort.isDescending();
                    }));
        }

        @Test
        @DisplayName("단일 브랜드 주문 목록 조회 — 응답에 대표 브랜드명 포함")
        void 주문_목록_브랜드명_포함() {
            // given — 한 주문이 한 브랜드(두바이도넛)의 상품만 담고 있는 일반적 케이스
            Event event = Event.builder().brandName("두바이도넛").build();
            Product product = Product.builder().event(event).build();
            OrderItem item = OrderItem.builder().product(product).build();
            Order order = Order.builder()
                    .user(user)
                    .orderItems(List.of(item))
                    .build();

            given(orderRepository.findByUserWithBrands(eq(user), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(order)));

            // when
            PageResponse<OrderResponse> response = orderService.getMyOrders(userDetails, 1, 10);

            // then — 응답에 brandName이 포함되고 첫 번째 item의 브랜드명이 나와야 함
            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getContent().get(0).getBrandName()).isEqualTo("두바이도넛");
        }

        @Test
        @DisplayName("다중 브랜드 주문 목록 조회 — 첫 번째 item의 브랜드만 대표로 반환")
        void 다중_브랜드_주문_대표_브랜드만() {
            // given — 한 주문에 서로 다른 브랜드 상품이 섞인 케이스
            // 현재 설계상 "대표 브랜드" = 첫 번째 OrderItem의 브랜드
            Event eventA = Event.builder().brandName("두바이도넛").build();
            Event eventB = Event.builder().brandName("다른브랜드").build();
            OrderItem item1 = OrderItem.builder()
                    .product(Product.builder().event(eventA).build()).build();
            OrderItem item2 = OrderItem.builder()
                    .product(Product.builder().event(eventB).build()).build();
            Order order = Order.builder()
                    .user(user)
                    .orderItems(List.of(item1, item2))
                    .build();

            given(orderRepository.findByUserWithBrands(eq(user), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(order)));

            // when
            PageResponse<OrderResponse> response = orderService.getMyOrders(userDetails, 1, 10);

            // then — 두 번째 브랜드(다른브랜드)는 응답에 나타나지 않고 대표값만 반환
            assertThat(response.getContent().get(0).getBrandName()).isEqualTo("두바이도넛");
        }

        @Test
        @DisplayName("orderItems가 비어있는 주문 — brandName은 null (방어 코드 검증)")
        void 빈_orderItems_brandName_null() {
            // given — 이론상 발생하지 않는 방어 케이스 (주문 생성 시 반드시 1개 이상의 item이 있음)
            // Order.getRepresentativeBrandName()이 빈 리스트에서 NPE를 던지지 않고 null을 반환하는지 검증
            Order order = Order.builder()
                    .user(user)
                    .orderItems(List.of())
                    .build();

            given(orderRepository.findByUserWithBrands(eq(user), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(order)));

            // when
            PageResponse<OrderResponse> response = orderService.getMyOrders(userDetails, 1, 10);

            // then — 예외 없이 null로 직렬화 가능한 상태로 응답
            assertThat(response.getContent().get(0).getBrandName()).isNull();
        }
    }

    // getOrderDetail — 주문 상세 조회
    @Nested
    @DisplayName("getOrderDetail")
    class GetOrderDetail {

        @Test
        @DisplayName("정상 조회 — 응답 필드 확인")
        void 정상_주문_상세_조회() {
            // given
            Order order = Order.builder()
                    .id(1L)
                    .user(user)
                    .orderNumber("ORD-20260414-000001")
                    .receiverName("홍길동")
                    .phone("010-1234-5678")
                    .address1("서울시 강남구")
                    .totalPrice(10000)
                    .status(OrderStatus.PENDING)
                    .build();

            // findByIdWithItems: OrderItem + Product를 한 번에 조회 (N+1 방지용 fetch join 쿼리)
            given(orderRepository.findByIdWithItems(1L)).willReturn(Optional.of(order));

            // when
            OrderDetailResponse response = orderService.getOrderDetail(1L, userDetails);

            // then
            assertThat(response.getOrderId()).isEqualTo(1L);
            assertThat(response.getReceiverName()).isEqualTo("홍길동");
        }

        @Test
        @DisplayName("존재하지 않는 주문 — OrderNotFoundException")
        void 존재하지_않는_주문_조회_예외() {
            // given
            given(orderRepository.findByIdWithItems(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> orderService.getOrderDetail(999L, userDetails))
                    .isInstanceOf(OrderNotFoundException.class);
        }

        @Test
        @DisplayName("주문 상세 조회 — 각 item에 brandName과 imageUrl 포함")
        void 주문_상세_item별_브랜드_이미지_포함() {
            // given — 브랜드(두바이도넛) 이벤트의 상품(초코두바이도넛, 이미지 URL 보유) 1개 주문
            Event event = Event.builder().brandName("두바이도넛").build();
            Product product = Product.builder()
                    .name("초코두바이도넛")
                    .imageUrl("https://s3.../choco.jpg")
                    .event(event)
                    .build();
            OrderItem item = OrderItem.builder()
                    .product(product)
                    .quantity(2)
                    .orderPrice(11000)
                    .build();
            Order order = Order.builder()
                    .id(1L)
                    .user(user)
                    .orderNumber("ORD-20260414-000001")
                    .receiverName("홍길동")
                    .phone("010-1234-5678")
                    .address1("서울시 강남구")
                    .totalPrice(11000)
                    .status(OrderStatus.PAID)
                    .orderItems(List.of(item))
                    .build();

            given(orderRepository.findByIdWithItems(1L)).willReturn(Optional.of(order));

            // when
            OrderDetailResponse response = orderService.getOrderDetail(1L, userDetails);

            // then — 각 item에 brandName과 imageUrl이 응답 필드로 포함되어야 함
            OrderDetailResponse.OrderItemDetail itemResult = response.getItems().get(0);
            assertThat(itemResult.getBrandName()).isEqualTo("두바이도넛");
            assertThat(itemResult.getImageUrl()).isEqualTo("https://s3.../choco.jpg");
            assertThat(itemResult.getProductName()).isEqualTo("초코두바이도넛");
        }

        @Test
        @DisplayName("타인의 주문 조회 — OrderNotFoundException (403 아닌 404로 존재 여부 숨김)")
        void 타인_주문_조회_예외() {
            // given — 주문 소유자(id=2L)와 요청 유저(id=1L)가 다른 상황
            // 403 대신 404를 반환하는 이유:
            // 403(권한 없음)을 반환하면 "해당 ID의 주문이 존재한다"는 정보가 노출됨
            // 404를 반환하면 존재 여부 자체를 알 수 없어 보안상 더 안전
            User anotherUser = User.builder()
                    .id(2L)
                    .email("other@test.com")
                    .password("password")
                    .nickname("다른유저")
                    .role(Role.USER)
                    .build();

            Order order = Order.builder()
                    .id(1L)
                    .user(anotherUser)
                    .build();

            given(orderRepository.findByIdWithItems(1L)).willReturn(Optional.of(order));

            // when & then
            assertThatThrownBy(() -> orderService.getOrderDetail(1L, userDetails))
                    .isInstanceOf(OrderNotFoundException.class);
        }
    }

    // cancelOrder — 주문 취소
    @Nested
    @DisplayName("cancelOrder")
    class CancelOrder {

        @Test
        @DisplayName("PENDING 상태 취소 성공 — CANCELED 상태로 변경, 재고 복구")
        void PENDING_주문_취소_성공() {
            // given
            Product product = Product.builder().id(1L).name("상품").price(1000).stock(5).event(openEvent).build();
            OrderItem item = OrderItem.builder().product(product).quantity(3).orderPrice(3000).build();

            Order order = Order.builder()
                    .id(1L)
                    .user(user)
                    .status(OrderStatus.PENDING)
                    .orderItems(List.of(item))
                    .build();

            given(orderRepository.findByIdWithItems(1L)).willReturn(Optional.of(order));

            // when
            OrderCancelResponse response = orderService.cancelOrder(1L, userDetails);

            // then — 상태가 CANCELED로 바뀌고, 재고가 5 → 8로 복구되어야 함
            assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELED);
            assertThat(product.getStock()).isEqualTo(8); // 5 + 3 = 8
        }

        @Test
        @DisplayName("PAID 상태 취소 성공 — PAID도 취소 가능, 재고 복구")
        void PAID_주문_취소_성공() {
            // given — 이미 결제 완료된 주문도 취소 가능
            Product product = Product.builder().id(1L).name("상품").price(1000).stock(0).event(openEvent).build();
            OrderItem item = OrderItem.builder().product(product).quantity(2).orderPrice(2000).build();

            Order order = Order.builder()
                    .id(1L)
                    .user(user)
                    .status(OrderStatus.PAID)
                    .orderItems(List.of(item))
                    .build();

            given(orderRepository.findByIdWithItems(1L)).willReturn(Optional.of(order));

            // when
            OrderCancelResponse response = orderService.cancelOrder(1L, userDetails);

            // then
            assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELED);
            assertThat(product.getStock()).isEqualTo(2); // 0 + 2 = 2
        }

        @Test
        @DisplayName("여러 상품 취소 — 모든 상품 재고 각각 복구")
        void 여러_상품_취소_재고_각각_복구() {
            // given — 상품A 2개, 상품B 3개 주문 후 취소
            Product productA = Product.builder().id(1L).name("상품A").price(1000).stock(0).event(openEvent).build();
            Product productB = Product.builder().id(2L).name("상품B").price(1000).stock(1).event(openEvent).build();

            OrderItem itemA = OrderItem.builder().product(productA).quantity(2).orderPrice(2000).build();
            OrderItem itemB = OrderItem.builder().product(productB).quantity(3).orderPrice(3000).build();

            Order order = Order.builder()
                    .id(1L)
                    .user(user)
                    .status(OrderStatus.PENDING)
                    .orderItems(List.of(itemA, itemB))
                    .build();

            given(orderRepository.findByIdWithItems(1L)).willReturn(Optional.of(order));

            // when
            orderService.cancelOrder(1L, userDetails);

            // then — 각 상품의 재고가 개별적으로 복구되었는지 확인
            assertThat(productA.getStock()).isEqualTo(2); // 0 + 2
            assertThat(productB.getStock()).isEqualTo(4); // 1 + 3
        }

        @Test
        @DisplayName("존재하지 않는 주문 취소 — OrderNotFoundException")
        void 존재하지_않는_주문_취소_예외() {
            // given
            given(orderRepository.findByIdWithItems(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> orderService.cancelOrder(999L, userDetails))
                    .isInstanceOf(OrderNotFoundException.class);
        }

        @Test
        @DisplayName("타인의 주문 취소 시도 — OrderNotFoundException")
        void 타인_주문_취소_예외() {
            // given — 로그인 유저(id=1L)가 다른 사람(id=2L)의 주문을 취소하려는 경우
            User anotherUser = User.builder()
                    .id(2L)
                    .email("other@test.com")
                    .password("password")
                    .nickname("다른유저")
                    .role(Role.USER)
                    .build();

            Order order = Order.builder()
                    .id(1L)
                    .user(anotherUser)
                    .orderItems(List.of())
                    .build();

            given(orderRepository.findByIdWithItems(1L)).willReturn(Optional.of(order));

            // when & then
            assertThatThrownBy(() -> orderService.cancelOrder(1L, userDetails))
                    .isInstanceOf(OrderNotFoundException.class);
        }

        @Test
        @DisplayName("이미 취소된 주문 재취소 — CANCEL_NOT_ALLOWED 예외")
        void 이미_취소된_주문_재취소_예외() {
            // given — 이미 CANCELED 상태인 주문을 또 취소하려는 경우
            Order order = Order.builder()
                    .id(1L)
                    .user(user)
                    .status(OrderStatus.CANCELED)
                    .orderItems(List.of())
                    .build();

            given(orderRepository.findByIdWithItems(1L)).willReturn(Optional.of(order));

            // when & then
            assertThatThrownBy(() -> orderService.cancelOrder(1L, userDetails))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CANCEL_NOT_ALLOWED);
        }

        @Test
        @DisplayName("구매 확정된 주문 취소 시도 — CANCEL_NOT_ALLOWED 예외")
        void 구매확정_주문_취소_예외() {
            // given — 배송 완료(COMPLETED) 상태는 취소 불가
            Order order = Order.builder()
                    .id(1L)
                    .user(user)
                    .status(OrderStatus.COMPLETED)
                    .orderItems(List.of())
                    .build();

            given(orderRepository.findByIdWithItems(1L)).willReturn(Optional.of(order));

            // when & then
            assertThatThrownBy(() -> orderService.cancelOrder(1L, userDetails))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CANCEL_NOT_ALLOWED);
        }
    }

    // 헬퍼 메서드 — 테스트용 객체 생성

    // CreateOrderRequest를 빌더로 생성 — mock() 대신 실제 객체 사용
    // mock()은 동작을 일일이 지정해야 해서 유지보수가 어렵고 실제 객체와 다를 수 있음
    private CreateOrderRequest buildRequest(List<CreateOrderRequest.OrderItemRequest> items) {
        return CreateOrderRequest.builder()
                .receiverName("홍길동")
                .phone("010-1234-5678")
                .zipcode("12345")
                .address1("서울시 강남구")
                .items(items)
                .build();
    }

    private CreateOrderRequest.OrderItemRequest buildItem(Long productId, int quantity) {
        return CreateOrderRequest.OrderItemRequest.builder()
                .productId(productId)
                .quantity(quantity)
                .build();
    }
}
