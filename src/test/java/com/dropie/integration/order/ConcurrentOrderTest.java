package com.dropie.integration.order;

import com.dropie.domain.event.entity.Event;
import com.dropie.domain.event.entity.EventStatus;
import com.dropie.domain.event.repository.EventRepository;
import com.dropie.domain.order.dto.request.CreateOrderRequest;
import com.dropie.domain.order.repository.OrderRepository;
import com.dropie.domain.order.service.CreateOrderUseCase;
import com.dropie.domain.product.entity.Product;
import com.dropie.domain.product.repository.ProductRepository;
import com.dropie.domain.user.entity.Role;
import com.dropie.domain.user.entity.User;
import com.dropie.domain.user.repository.UserRepository;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import com.dropie.global.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "app.lock.type=redis")
public class ConcurrentOrderTest {

    @Autowired
    private CreateOrderUseCase createOrderUseCase;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long targetProductId;
    private User user1, user2;

    @BeforeEach
    void setUp() {
        // 테스트용 OPEN 이벤트 — 현재 시각 기준으로 startAt/endAt을 잡아야
        // OrderService.validateEventTime() 체크를 통과할 수 있음
        Event event = eventRepository.save(Event.builder()
                .brandName("동시성테스트브랜드")
                .description("테스트 전용")
                .thumbnailImageUrl("https://test.jpg")
                .imageUrl("https://test.jpg")
                .startAt(LocalDateTime.now().minusHours(1))
                .endAt(LocalDateTime.now().plusHours(1))
                .status(EventStatus.OPEN)
                .build());

        Product product = productRepository.save(Product.builder()
                .event(event)
                .name("재고1개_동시성테스트상품")
                .imageUrl("https://test.jpg")
                .description("테스트 전용")
                .price(5000)
                .stock(1)
                .build());
        targetProductId = product.getId();

        user1 = userRepository.save(User.builder()
                .email("concurrent1@test.com")
                .password(passwordEncoder.encode("password"))
                .nickname("테스터1")
                .role(Role.USER)
                .build());
        user2 = userRepository.save(User.builder()
                .email("concurrent2@test.com")
                .password(passwordEncoder.encode("password"))
                .nickname("테스터2")
                .role(Role.USER)
                .build());
    }

    @AfterEach
    void tearDown() {
        // H2 in-memory DB는 테스트 간 상태가 공유되므로 수동 정리 필요
        // 외래키 제약 순서: Order(OrderItem 포함) → Product → Event → User
        orderRepository.deleteAll();
        productRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("재고 1개 상품에 동시 주문 2건 — 1건 성공, 1건 실패, 최종 재고 0")
    void 동시_주문_재고1개_하나만_성공() throws InterruptedException {
        int threadCount = 2;

        // readyLatch — 각 스레드가 준비 완료 시 카운트다운 → 메인이 모든 준비를 확인
        // startLatch — 메인이 countDown(1→0)으로 '동시 출발' 신호를 보냄
        // doneLatch  — 각 스레드가 완료 시 카운트다운 → 메인이 모든 완료를 기다림
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        List<User> users = List.of(user1, user2);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            new Thread(() -> {
                try {
                    CustomUserDetails userDetails = new CustomUserDetails(users.get(idx));
                    CreateOrderRequest request = CreateOrderRequest.builder()
                            .receiverName("수령인" + idx)
                            .phone("010-0000-000" + idx)
                            .zipcode("06001")
                            .address1("서울시 강남구 테헤란로 1")
                            .items(List.of(
                                    CreateOrderRequest.OrderItemRequest.builder()
                                            .productId(targetProductId)
                                            .quantity(1)
                                            .build()
                            ))
                            .build();

                    readyLatch.countDown();
                    startLatch.await();

                    createOrderUseCase.createOrder(request, userDetails);
                    successCount.incrementAndGet();

                } catch (BusinessException e) {
                    System.out.println("예외 코드 = " + e.getErrorCode());
                    failCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);

        assertThat(orderRepository.countByOrderedProductId(targetProductId)).isEqualTo(1);

        Product product = productRepository.findById(targetProductId).orElseThrow();
        assertThat(product.getStock()).isEqualTo(0);
    }
}

