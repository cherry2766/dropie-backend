package com.dropie.domain.order.service;

import com.dropie.domain.event.entity.Event;
import com.dropie.domain.event.entity.EventStatus;
import com.dropie.domain.event.repository.EventRepository;
import com.dropie.domain.order.dto.request.CreateOrderRequest;
import com.dropie.domain.order.listener.PendingOrderExpirationListener;
import com.dropie.domain.order.repository.OrderItemRepository;
import com.dropie.domain.order.repository.OrderRepository;
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
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

// @Transactional 사용 금지
// 테스트 메서드에 @Transactional이 붙으면 각 스레드의 트랜잭션이 하나로 묶여
// 동시성 테스트가 의미 없어짐
@SpringBootTest
@TestPropertySource(properties = "app.lock.type=optimistic")
class OptimisticLockConcurrencyTest {

    @Autowired private OptimisticLockOrderFacade optimisticLockOrderFacade;
    @Autowired private ProductRepository productRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;

    // RedisConfig의 RedissonClient Bean이 Redis 연결을 시도하는 걸 방지
    @MockitoBean
    private RedissonClient redissonClient;

    // RateLimitInterceptor가 필요로 하는 StringRedisTemplate — 실제 Redis 없이 컨텍스트 로드 가능하게 Mock 처리
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    // EmailVerificationService가 필요로 하는 JavaMailSender — 테스트 환경에서 실제 메일 서버 없이 컨텍스트 로드 가능하게 Mock 처리
    @MockitoBean
    private JavaMailSender javaMailSender;

    // PendingOrderExpirationListener는 부모(KeyExpirationEventMessageListener)의 afterPropertiesSet()이
    // RedisConnectionFactory.getConnection()을 호출함 → mock 처리한 RedissonClient 때문에 NPE 발생
    // 동시성 테스트에서는 만료 리스너가 불필요하므로 빈 자체를 Mock으로 대체
    @MockitoBean
    private PendingOrderExpirationListener pendingOrderExpirationListener;

    private static final int INITIAL_STOCK = 50;
    private static final int THREAD_COUNT = 100;

    private Product product;
    private User testUser;

    @BeforeEach
    void setUp() {
        // StringRedisTemplate Mock 스텁
        // → OrderService.generateOrderNumber()가 redisTemplate.opsForValue().increment(key)를 호출함
        // → Mock 기본 동작은 opsForValue()가 null을 반환 → NPE → 모든 주문이 실패로 잡힘
        // → AtomicLong으로 INCR을 인메모리 재현 (Redis INCR과 동일하게 원자적 시퀀스 보장)
        AtomicLong orderSeq = new AtomicLong(0);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.increment(anyString())).willAnswer(inv -> orderSeq.incrementAndGet());

        // 판매 중인 이벤트 — validateEventTime() 통과를 위해 현재 시각 기준으로 설정
        Event event = eventRepository.save(Event.builder()
                .brandName("동시성 테스트 브랜드")
                .status(EventStatus.OPEN)
                .startAt(LocalDateTime.now().minusHours(1))
                .endAt(LocalDateTime.now().plusHours(1))
                .build());

        product = productRepository.save(Product.builder()
                .event(event)
                .name("한정판 테스트 상품")
                .price(10000)
                .stock(INITIAL_STOCK)
                .build());

        testUser = userRepository.save(User.builder()
                .email("concurrency@test.com")
                .password("encoded_password")
                .nickname("동시성테스터")
                .role(Role.USER)
                .build());
    }

    @AfterEach
    void tearDown() {
        // FK 제약 순서에 맞게 자식 테이블부터 삭제
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("낙관적 락 — 100명 동시 주문, 재고 50개 → 정합성 보장되지만 재시도 소진으로 미판매 발생")
    void 낙관적_락_100명_동시_주문_재고_정합성() throws InterruptedException {
        // 풀 크기 = 스레드 수: 100개가 진짜로 동시에 실행되어야 버전 충돌이 발생함
        // newFixedThreadPool(32)처럼 풀이 작으면 순차 실행에 가까워져 충돌이 거의 안 일어남
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        // startLatch: 모든 스레드가 준비된 후 동시에 출발시키기 위한 신호탄
        CountDownLatch startLatch = new CountDownLatch(1);
        // doneLatch: 모든 스레드가 완료될 때까지 메인 스레드 대기
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);

        AtomicInteger successCount = new AtomicInteger(0);
        // ORDER_CONFLICT: @Retryable 재시도를 3번 모두 소진한 실패 → 낙관적 락의 핵심 한계
        // 재고가 남아있는데도 버전 충돌이 반복되면 구매에 실패함
        AtomicInteger orderConflictCount = new AtomicInteger(0);
        // OUT_OF_STOCK / EVENT_ENDED: 재고 소진 또는 이벤트 마감으로 인한 정상 실패
        AtomicInteger outOfStockCount = new AtomicInteger(0);

        CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                .receiverName("강체리")
                .phone("010-1234-5678")
                .zipcode("12345")
                .address1("서울시 강남구")
                .address2("101호")
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder()
                                .productId(product.getId())
                                .quantity(1)
                                .build()
                ))
                .build();

        CustomUserDetails userDetails = new CustomUserDetails(testUser);

        for (int i = 0; i < THREAD_COUNT; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await(); // 신호탄 기다림
                    optimisticLockOrderFacade.createOrder(orderRequest, userDetails);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.ORDER_CONFLICT) {
                        // 재시도를 3번 했지만 계속 충돌 → 재고가 있어도 구매 실패
                        orderConflictCount.incrementAndGet();
                    } else {
                        // OUT_OF_STOCK, EVENT_ENDED 등 재고 소진 이후의 정상 실패
                        outOfStockCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    // ExhaustedRetryException: @Recover 탐색 실패 시 발생 — 충돌 실패로 집계
                    orderConflictCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 100개 스레드 동시 출발
        doneLatch.await();      // 전부 완료될 때까지 대기
        executorService.shutdown();

        Product updated = productRepository.findById(product.getId()).get();

        // 검증 1: 재고 음수 없음 — 낙관적 락이 정합성은 보장
        assertThat(updated.getStock()).isGreaterThanOrEqualTo(0);
        // 검증 2: 성공 건수 + 잔여 재고 = 초기 재고 (차감된 재고 = 성공한 주문 수)
        assertThat(successCount.get() + updated.getStock()).isEqualTo(INITIAL_STOCK);

        System.out.printf(
                "[낙관적 락] 성공: %d건 / ORDER_CONFLICT(재시도 소진): %d건 / 재고소진: %d건 / 잔여 재고: %d개%n",
                successCount.get(), orderConflictCount.get(), outOfStockCount.get(), updated.getStock()
        );
        System.out.printf(
                "→ 재고가 있는데 못 판 수량: %d개 (ORDER_CONFLICT로 인한 미판매)%n",
                updated.getStock()
        );
    }

    // 이력서·포폴 수치 산출용 자동 측정 — 15회 반복 후 평균/최소/최대 출력
    // 매 회차마다 DB를 리셋해 독립된 동일 조건으로 재현
    @Test
    @DisplayName("낙관적 락 — 15회 반복 측정 (이력서 수치 산출용)")
    void 낙관적_락_15회_반복_측정() throws InterruptedException {
        int runs = 15;
        int[][] results = new int[runs][3]; // [success, conflict, remaining]

        System.out.println();
        System.out.println("========== 낙관적 락 15회 반복 측정 ==========");
        System.out.printf("초기 재고: %d / 동시 주문: %d스레드%n", INITIAL_STOCK, THREAD_COUNT);
        System.out.println("----------------------------------------------");

        for (int run = 1; run <= runs; run++) {
            // FK 순서대로 자식 → 부모 정리
            orderItemRepository.deleteAll();
            orderRepository.deleteAll();
            productRepository.deleteAll();
            eventRepository.deleteAll();
            userRepository.deleteAll();

            Event event = eventRepository.save(Event.builder()
                    .brandName("측정 브랜드")
                    .status(EventStatus.OPEN)
                    .startAt(LocalDateTime.now().minusHours(1))
                    .endAt(LocalDateTime.now().plusHours(1))
                    .build());
            Product runProduct = productRepository.save(Product.builder()
                    .event(event)
                    .name("측정용 상품")
                    .price(10000)
                    .stock(INITIAL_STOCK)
                    .build());
            User runUser = userRepository.save(User.builder()
                    .email("measure" + run + "@test.com")
                    .password("encoded_password")
                    .nickname("측정" + run)
                    .role(Role.USER)
                    .build());

            int[] r = runConcurrentOptimisticOrders(runProduct, runUser);
            results[run - 1] = r;

            System.out.printf("[Run %2d] 성공 %2d건 / 재시도 소진 %2d건 / 잔여 재고 %2d개%n",
                    run, r[0], r[1], r[2]);
        }

        // 통계 산출
        int sumSuccess = 0, sumConflict = 0, sumRemaining = 0;
        int minSuccess = Integer.MAX_VALUE, maxSuccess = Integer.MIN_VALUE;
        int minConflict = Integer.MAX_VALUE, maxConflict = Integer.MIN_VALUE;
        int minRemaining = Integer.MAX_VALUE, maxRemaining = Integer.MIN_VALUE;
        for (int[] r : results) {
            sumSuccess += r[0]; sumConflict += r[1]; sumRemaining += r[2];
            minSuccess = Math.min(minSuccess, r[0]); maxSuccess = Math.max(maxSuccess, r[0]);
            minConflict = Math.min(minConflict, r[1]); maxConflict = Math.max(maxConflict, r[1]);
            minRemaining = Math.min(minRemaining, r[2]); maxRemaining = Math.max(maxRemaining, r[2]);
        }
        double avgSuccess = sumSuccess / (double) runs;
        double avgConflict = sumConflict / (double) runs;
        double avgRemaining = sumRemaining / (double) runs;

        System.out.println("----------------------------------------------");
        System.out.printf("[평균]    성공 %.2f건 / 재시도 소진 %.2f건 / 잔여 재고 %.2f개%n",
                avgSuccess, avgConflict, avgRemaining);
        System.out.printf("[최소~최대] 성공 %d~%d / 재시도 소진 %d~%d / 잔여 재고 %d~%d%n",
                minSuccess, maxSuccess, minConflict, maxConflict, minRemaining, maxRemaining);
        System.out.printf("[미판매율] 평균 %.2f%% (잔여재고 / 초기재고)%n",
                avgRemaining / INITIAL_STOCK * 100);
        System.out.printf("[재시도 소진률] 평균 %.2f%% (재시도 소진 / 동시 주문)%n",
                avgConflict / THREAD_COUNT * 100);
        System.out.println("==============================================");
    }

    private int[] runConcurrentOptimisticOrders(Product runProduct, User runUser) throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger orderConflictCount = new AtomicInteger(0);

        CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                .receiverName("강체리")
                .phone("010-1234-5678")
                .zipcode("12345")
                .address1("서울시 강남구")
                .address2("101호")
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder()
                                .productId(runProduct.getId())
                                .quantity(1)
                                .build()
                ))
                .build();
        CustomUserDetails userDetails = new CustomUserDetails(runUser);

        for (int i = 0; i < THREAD_COUNT; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    optimisticLockOrderFacade.createOrder(orderRequest, userDetails);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.ORDER_CONFLICT) {
                        orderConflictCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    orderConflictCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        doneLatch.await();
        executorService.shutdown();

        Product updated = productRepository.findById(runProduct.getId()).get();
        return new int[]{successCount.get(), orderConflictCount.get(), updated.getStock()};
    }
}
