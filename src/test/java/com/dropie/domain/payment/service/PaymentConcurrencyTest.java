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
import com.dropie.domain.payment.dto.response.PaymentConfirmResponse;
import com.dropie.domain.payment.dto.response.TossConfirmResponse;
import com.dropie.domain.payment.repository.PaymentRepository;
import com.dropie.domain.product.entity.Product;
import com.dropie.domain.product.repository.ProductRepository;
import com.dropie.domain.user.entity.Role;
import com.dropie.domain.user.entity.User;
import com.dropie.domain.user.repository.UserRepository;
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

// 결제 멱등성 통합 테스트
// → PaymentService.confirmPayment()는 비관적 락(findByIdForUpdate) + PAID 멱등 체크의 2중 방어를 가짐
//   ① 비관적 락: 같은 orderId 동시 요청을 한 줄로 직렬화
//   ② 멱등 체크: 두 번째 요청이 락을 얻은 시점엔 이미 PAID이므로 토스 호출 없이 기존 Payment 반환
// → 두 방어가 함께 동작해 "토스 API 1회만 호출 + 두 응답 모두 동일 paymentKey"를 보장하는지 검증
//
// 단위 테스트(PaymentServiceTest)는 단일 스레드 흐름만 보므로 이 시나리오를 못 잡음.
// 통합 테스트로 실제 DB·트랜잭션·락이 동작하는 환경에서 검증해야 의미 있음.
//
// 트랜잭션 주의:
// 클래스/메서드에 @Transactional을 붙이면 모든 스레드가 한 트랜잭션으로 묶여
// 동시성 시나리오가 사라짐 → 일부러 안 붙임.
@SpringBootTest
class PaymentConcurrencyTest {

    @Autowired private PaymentService paymentService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PaymentRepository paymentRepository;

    // 토스 호출 횟수와 응답을 통제하기 위해 mock — 실제 외부 API는 호출하지 않음
    @MockitoBean
    private TossPaymentClient tossPaymentClient;

    // 컨텍스트 로드를 위한 외부 의존 Mock
    @MockitoBean
    private RedissonClient redissonClient;
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;
    @MockitoBean
    private JavaMailSender javaMailSender;
    @MockitoBean
    private PendingOrderExpirationListener pendingOrderExpirationListener;

    // 주문 생성 시점에 ORDER_QUANTITY만큼 차감되었다고 가정 — INITIAL_STOCK - ORDER_QUANTITY 가 setUp의 stock
    private static final int INITIAL_STOCK = 10;
    private static final int ORDER_QUANTITY = 1;
    private static final int PRICE = 10000;
    private static final String PAYMENT_KEY = "payKey-concurrent-xyz";

    private User testUser;
    private Product product;
    private Order order;

    @BeforeEach
    void setUp() {
        // 판매 중인 이벤트
        Event event = eventRepository.save(Event.builder()
                .brandName("멱등성 테스트 브랜드")
                .status(EventStatus.OPEN)
                .startAt(LocalDateTime.now().minusHours(1))
                .endAt(LocalDateTime.now().plusHours(1))
                .build());

        // 주문 생성으로 이미 1개 차감된 상태(stock = 9)
        product = productRepository.save(Product.builder()
                .event(event)
                .name("테스트 상품")
                .price(PRICE)
                .stock(INITIAL_STOCK - ORDER_QUANTITY)
                .build());

        testUser = userRepository.save(User.builder()
                .email("idempotency@test.com")
                .password("encoded_password")
                .nickname("멱등성테스터")
                .role(Role.USER)
                .build());

        // PENDING 상태의 주문 + OrderItem (재고 차감된 상태에서 결제 대기)
        Order pending = Order.builder()
                .user(testUser)
                .orderNumber("ORD-20260508-000001")
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
        paymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("결제 멱등성 — 동일 orderId로 confirm 동시 2회 → 토스 1회만 호출 + 두 응답 모두 동일 paymentKey")
    void 결제_확인_동시_2회_멱등성_보장() throws InterruptedException {
        // given: 토스 응답은 200ms sleep 후 반환되도록 stub
        // → A 스레드가 토스 호출 중일 때 B 스레드는 비관적 락 앞에서 진짜로 대기하게 됨
        //   (mock이 즉시 응답하면 A가 너무 빨리 끝나서 B가 락 풀린 뒤에야 들어와 동시성 시나리오가 약해짐)
        TossConfirmResponse tossResponse = TossConfirmResponse.builder()
                .paymentKey(PAYMENT_KEY)
                .orderId(order.getOrderNumber())
                .totalAmount(PRICE)
                .method("카드")
                .approvedAt("2026-05-08T12:00:00+09:00")
                .status("DONE")
                .build();

        given(tossPaymentClient.confirm(anyString(), anyString(), anyInt()))
                .willAnswer(invocation -> {
                    Thread.sleep(200);
                    return tossResponse;
                });

        PaymentConfirmRequest request = new PaymentConfirmRequest(PAYMENT_KEY, PRICE);

        // when: 2개 스레드가 동시에 같은 orderId로 confirm 호출
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        // 두 호출의 응답을 모아 paymentKey 동일성 검증에 사용
        // CopyOnWriteArrayList: 두 스레드가 동시에 add() 해도 안전
        List<PaymentConfirmResponse> responses = new CopyOnWriteArrayList<>();

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // 신호탄 대기
                    PaymentConfirmResponse res = paymentService.confirmPayment(
                            testUser.getEmail(), order.getId(), request);
                    responses.add(res);
                    successCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 두 스레드 동시 출발
        doneLatch.await();
        executor.shutdown();

        // then 1: 두 호출 모두 정상 응답 (예외 없이 종료)
        assertThat(successCount.get())
                .as("두 호출 모두 정상 응답을 받아야 함")
                .isEqualTo(2);
        assertThat(failCount.get()).isZero();

        // then 2: 토스 승인 API는 정확히 1회만 호출 (두 번째는 PAID 멱등 응답으로 토스 안 탐)
        // → 멱등성의 핵심 — 외부 시스템(토스) 입장에서 중복 결제 발생 안 함
        then(tossPaymentClient).should(times(1))
                .confirm(anyString(), anyString(), anyInt());

        // then 3: 두 응답 모두 동일한 paymentKey/amount 반환
        // → 두 번째 호출자도 첫 번째와 동일한 결제 결과를 보게 됨
        assertThat(responses).hasSize(2);
        assertThat(responses).allSatisfy(res -> {
            assertThat(res.getPaymentKey()).isEqualTo(PAYMENT_KEY);
            assertThat(res.getAmount()).isEqualTo(PRICE);
            assertThat(res.getStatus()).isEqualTo(OrderStatus.PAID.name());
        });

        // then 4: DB 상태 — 주문 PAID + Payment 1건만 저장 (중복 저장 없음)
        Order persisted = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(OrderStatus.PAID);

        List<com.dropie.domain.payment.entity.Payment> payments =
                paymentRepository.findAll();
        assertThat(payments)
                .as("Payment는 정확히 1건만 저장되어야 함 (중복 저장 방지)")
                .hasSize(1);
        assertThat(payments.get(0).getPaymentKey()).isEqualTo(PAYMENT_KEY);

        // 콘솔 출력 — 결과 확인용
        System.out.println();
        System.out.printf("[결제 멱등성] 동시 호출: 2회 / 토스 API 호출: 1회 / Payment 저장: %d건 / 응답 paymentKey: %s%n",
                payments.size(),
                Collections.singleton(PAYMENT_KEY));
    }
}
