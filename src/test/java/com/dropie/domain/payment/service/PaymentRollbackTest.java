package com.dropie.domain.payment.service;

import com.dropie.domain.event.entity.Event;
import com.dropie.domain.event.entity.EventStatus;
import com.dropie.domain.event.repository.EventRepository;
import com.dropie.domain.order.entity.Order;
import com.dropie.domain.order.entity.OrderItem;
import com.dropie.domain.order.entity.OrderStatus;
import com.dropie.domain.order.listener.PendingOrderExpirationListener;
import com.dropie.domain.order.repository.OrderItemRepository;
import com.dropie.domain.order.repository.OrderRepository;
import com.dropie.domain.payment.client.TossPaymentClient;
import com.dropie.domain.payment.dto.request.PaymentConfirmRequest;
import com.dropie.domain.product.entity.Product;
import com.dropie.domain.product.repository.ProductRepository;
import com.dropie.domain.user.entity.Role;
import com.dropie.domain.user.entity.User;
import com.dropie.domain.user.repository.UserRepository;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

// 보상 트랜잭션(rollback) 통합 테스트
// → PaymentService.confirmPayment()가 토스 API 실패 시 rollbackOrder()로 주문 취소 + 재고 복원하는데,
//   이 변경이 dirty-checking으로 처리되는 동시에 catch 블록에서 throw로 트랜잭션 자체가 롤백됨
// → dirty-checking 변경이 같이 무효화되는지(= 보상이 안 먹히는지)를 통합 테스트로 검증
//
// 단위 테스트(PaymentServiceTest)는 in-memory Order 객체 상태만 보므로 이 이슈를 잡을 수 없음.
// 통합 테스트는 실제 DB에 반영되는지를 확인해야 의미 있음.
@SpringBootTest
class PaymentRollbackTest {

    @Autowired private PaymentService paymentService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private UserRepository userRepository;

    // 토스 API는 무조건 실패하도록 stub — 실제 호출 안 함
    @MockitoBean
    private TossPaymentClient tossPaymentClient;

    // 컨텍스트 로드를 위한 외부 의존 Mock (낙관적 락 테스트와 동일 패턴)
    @MockitoBean
    private RedissonClient redissonClient;
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;
    @MockitoBean
    private JavaMailSender javaMailSender;
    @MockitoBean
    private PendingOrderExpirationListener pendingOrderExpirationListener;

    // 초기 재고 — 주문 생성 시점에 ORDER_QUANTITY만큼 차감되었다고 가정
    private static final int INITIAL_STOCK = 10;
    private static final int ORDER_QUANTITY = 1;
    private static final int PRICE = 10000;

    private User testUser;
    private Product product;
    private Order order;

    @BeforeEach
    void setUp() {
        // 판매 중인 이벤트
        Event event = eventRepository.save(Event.builder()
                .brandName("롤백 테스트 브랜드")
                .status(EventStatus.OPEN)
                .startAt(LocalDateTime.now().minusHours(1))
                .endAt(LocalDateTime.now().plusHours(1))
                .build());

        // 주문 생성으로 이미 1개 차감된 상태(stock = 9)
        // → 결제 실패 시 stock이 다시 10으로 복원되어야 정합성 유지
        product = productRepository.save(Product.builder()
                .event(event)
                .name("테스트 상품")
                .price(PRICE)
                .stock(INITIAL_STOCK - ORDER_QUANTITY)
                .build());

        testUser = userRepository.save(User.builder()
                .email("rollback@test.com")
                .password("encoded_password")
                .nickname("롤백테스터")
                .role(Role.USER)
                .build());

        // PENDING 상태의 주문 + OrderItem (재고 차감된 상태에서 결제 대기)
        Order pending = Order.builder()
                .user(testUser)
                .orderNumber("ORD-20260507-000001")
                .receiverName("강체리")
                .phone("010-1234-5678")
                .zipcode("12345")
                .address1("서울시 강남구")
                .address2("101호")
                .totalPrice(PRICE)
                .status(OrderStatus.PENDING)
                .build();

        pending.addOrderItem(OrderItem.builder()
                .order(pending)
                .product(product)
                .quantity(ORDER_QUANTITY)
                .orderPrice(PRICE)
                .build());

        order = orderRepository.save(pending);
    }

    @AfterEach
    void tearDown() {
        // FK 제약 순서: 자식 → 부모
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("토스 API 실패 → DB에 보상 트랜잭션 반영 (주문 CANCELED + 재고 복원)")
    void 토스_실패_시_DB에_보상_반영() {
        // given: 토스 호출 시 무조건 BusinessException
        // → confirmPayment()가 catch 블록에서 rollbackOrder() 후 throw e
        given(tossPaymentClient.confirm(anyString(), anyString(), anyInt()))
                .willThrow(new BusinessException(ErrorCode.PAYMENT_FAILED));

        PaymentConfirmRequest request = new PaymentConfirmRequest("payKey-fail", PRICE);

        // when: 결제 confirm 호출 → 예외 전파 확인
        assertThatThrownBy(() ->
                paymentService.confirmPayment(testUser.getEmail(), order.getId(), request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_FAILED);

        // then 1: DB에서 주문 상태 재조회 — CANCELED여야 함
        // ⚠️ 여기서 실패하면 dirty-checking 변경이 트랜잭션 롤백으로 함께 무효화된 것
        // → 같은 @Transactional 안에서 변경 후 throw하면 변경도 같이 사라지는 패턴
        // → 수정 옵션: rollbackOrder를 REQUIRES_NEW 별도 트랜잭션으로, 또는 AFTER_ROLLBACK 이벤트로 분리
        Order rolled = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(rolled.getStatus())
                .as("토스 실패 시 주문이 CANCELED로 전환되어야 함")
                .isEqualTo(OrderStatus.CANCELED);

        // then 2: 재고가 원래대로(INITIAL_STOCK) 복원되어야 함
        Product restored = productRepository.findById(product.getId()).orElseThrow();
        assertThat(restored.getStock())
                .as("토스 실패 시 차감되었던 재고가 복원되어야 함 (재고 누수 방지)")
                .isEqualTo(INITIAL_STOCK);
    }
}
