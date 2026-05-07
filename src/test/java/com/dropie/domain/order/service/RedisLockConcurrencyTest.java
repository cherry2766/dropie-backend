package com.dropie.domain.order.service;

import com.dropie.domain.event.entity.Event;
import com.dropie.domain.event.entity.EventStatus;
import com.dropie.domain.event.repository.EventRepository;
import com.dropie.domain.order.dto.request.CreateOrderRequest;
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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@SpringBootTest
@TestPropertySource(properties = "app.lock.type=redis")
public class RedisLockConcurrencyTest {

    @Autowired private RedisLockOrderFacade redisLockOrderFacade;
    @Autowired private ProductRepository productRepository;

    // RateLimitInterceptor가 필요로 하는 StringRedisTemplate — 실제 Redis 없이 컨텍스트 로드 가능하게 Mock 처리
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    // EmailVerificationService가 필요로 하는 JavaMailSender — 테스트 환경에서 실제 메일 서버 없이 컨텍스트 로드 가능하게 Mock 처리
    @MockitoBean
    private JavaMailSender javaMailSender;
    @Autowired private EventRepository eventRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;

    private static final int INITIAL_STOCK = 50;
    private static final int THREAD_COUNT = 100;

    private Product product;
    private User testUser;

    @BeforeEach
    void setUp() {
        // OrderService.generateOrderNumber()가 redisTemplate.opsForValue().increment(key)를 호출하므로
        // 매 호출마다 unique한 값을 반환하도록 mock 세팅 (orderNumber가 unique 제약)
        // 또한 PENDING TTL 등록을 위한 set(...)도 NPE 안 나도록 stub
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        AtomicLong seq = new AtomicLong(0);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.increment(anyString())).willAnswer(inv -> seq.incrementAndGet());

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
                .email("test@test.com")
                .password("encoded_password")
                .nickname("Redis테스터")
                .role(Role.USER)
                .build());
    }

    @AfterEach
    void tearDown() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Redis 분산 락 — 100명 동시 주문, 재고 50개 → 정확히 50건 성공, 잔여 재고 0")
    void Redis_분산_락_100명_동시_주문_전원_처리() throws InterruptedException {
        // 풀 크기 = 스레드 수: 낙관적 락 테스트(THREAD_COUNT=100)와 동일 조건으로 비교 측정
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger outOfStockCount = new AtomicInteger(0);  // 재고 소진 실패
        AtomicInteger lockFailCount = new AtomicInteger(0);    // 락 획득 실패 (waitTime 초과)

        CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                .receiverName("포도알")
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
                    startLatch.await();
                    redisLockOrderFacade.createOrder(orderRequest, userDetails);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    // LOCK_ACQUISITION_FAILED: waitTime 초과로 락 자체를 못 잡은 실패 → Redis 락의 한계
                    // OUT_OF_STOCK, EVENT_ENDED 등: 재고 소진 또는 이벤트 마감으로 인한 정상 실패
                    //   (50번째 주문이 완료되면 이벤트가 자동 SOLD_OUT → 이후 스레드는 EVENT_ENDED 예외)
                    if (e.getErrorCode() == ErrorCode.LOCK_ACQUISITION_FAILED) {
                        lockFailCount.incrementAndGet();
                    } else {
                        outOfStockCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executorService.shutdown();

        Product updated = productRepository.findById(product.getId()).get();

        // 핵심 검증 1: 재고 음수 없음
        assertThat(updated.getStock()).isGreaterThanOrEqualTo(0);
        // 핵심 검증 2: 성공 + 잔여 재고 = 초기 재고 (정합성)
        assertThat(successCount.get() + updated.getStock()).isEqualTo(INITIAL_STOCK);
        // Redis 분산 락 핵심 검증: 재고 내에서 요청한 모든 주문이 성공
        // (재시도 소진 실패가 없음 — 낙관적 락과의 차이점)
        assertThat(lockFailCount.get()).isEqualTo(0);
        // 재고 50개이므로 정확히 50건 성공, 나머지 50건은 OUT_OF_STOCK
        assertThat(successCount.get()).isEqualTo(INITIAL_STOCK);
        assertThat(updated.getStock()).isEqualTo(0);

        System.out.printf("[Redis 분산 락] 성공: %d건 / 주문 불가(재고소진·이벤트마감): %d건 / 락 획득 실패: %d건%n",
                successCount.get(), outOfStockCount.get(), lockFailCount.get());
    }

    // 이력서·포폴 수치 산출용 자동 측정 — 15회 반복 후 평균/최소/최대 출력
    // 매 회차마다 DB를 리셋해 독립된 동일 조건으로 재현 (Redis 락은 자체 키이므로 별도 정리 불요)
    @Test
    @DisplayName("Redis 분산 락 — 15회 반복 측정 (이력서 수치 산출용)")
    void Redis_분산_락_15회_반복_측정() throws InterruptedException {
        int runs = 15;
        int[][] results = new int[runs][3]; // [success, lockFail, remaining]

        System.out.println();
        System.out.println("========== Redis 분산 락 15회 반복 측정 ==========");
        System.out.printf("초기 재고: %d / 동시 주문: %d스레드%n", INITIAL_STOCK, THREAD_COUNT);
        System.out.println("--------------------------------------------------");

        for (int run = 1; run <= runs; run++) {
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
                    .email("redis-measure" + run + "@test.com")
                    .password("encoded_password")
                    .nickname("Redis측정" + run)
                    .role(Role.USER)
                    .build());

            int[] r = runConcurrentRedisOrders(runProduct, runUser);
            results[run - 1] = r;

            System.out.printf("[Run %2d] 성공 %2d건 / 락 획득 실패 %2d건 / 잔여 재고 %2d개%n",
                    run, r[0], r[1], r[2]);
        }

        int sumSuccess = 0, sumLockFail = 0, sumRemaining = 0;
        int minSuccess = Integer.MAX_VALUE, maxSuccess = Integer.MIN_VALUE;
        int minLockFail = Integer.MAX_VALUE, maxLockFail = Integer.MIN_VALUE;
        int minRemaining = Integer.MAX_VALUE, maxRemaining = Integer.MIN_VALUE;
        for (int[] r : results) {
            sumSuccess += r[0]; sumLockFail += r[1]; sumRemaining += r[2];
            minSuccess = Math.min(minSuccess, r[0]); maxSuccess = Math.max(maxSuccess, r[0]);
            minLockFail = Math.min(minLockFail, r[1]); maxLockFail = Math.max(maxLockFail, r[1]);
            minRemaining = Math.min(minRemaining, r[2]); maxRemaining = Math.max(maxRemaining, r[2]);
        }
        double avgSuccess = sumSuccess / (double) runs;
        double avgLockFail = sumLockFail / (double) runs;
        double avgRemaining = sumRemaining / (double) runs;

        System.out.println("--------------------------------------------------");
        System.out.printf("[평균]    성공 %.2f건 / 락 획득 실패 %.2f건 / 잔여 재고 %.2f개%n",
                avgSuccess, avgLockFail, avgRemaining);
        System.out.printf("[최소~최대] 성공 %d~%d / 락 획득 실패 %d~%d / 잔여 재고 %d~%d%n",
                minSuccess, maxSuccess, minLockFail, maxLockFail, minRemaining, maxRemaining);
        System.out.printf("[미판매율] 평균 %.2f%% (잔여재고 / 초기재고)%n",
                avgRemaining / INITIAL_STOCK * 100);
        System.out.println("==================================================");
    }

    private int[] runConcurrentRedisOrders(Product runProduct, User runUser) throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger lockFailCount = new AtomicInteger(0);

        CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                .receiverName("포도알")
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
                    redisLockOrderFacade.createOrder(orderRequest, userDetails);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.LOCK_ACQUISITION_FAILED) {
                        lockFailCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        doneLatch.await();
        executorService.shutdown();

        Product updated = productRepository.findById(runProduct.getId()).get();
        return new int[]{successCount.get(), lockFailCount.get(), updated.getStock()};
    }
}
