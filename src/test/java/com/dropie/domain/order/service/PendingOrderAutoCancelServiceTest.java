package com.dropie.domain.order.service;

import com.dropie.domain.event.entity.Event;
import com.dropie.domain.event.entity.EventStatus;
import com.dropie.domain.order.entity.Order;
import com.dropie.domain.order.entity.OrderItem;
import com.dropie.domain.order.entity.OrderStatus;
import com.dropie.domain.order.repository.OrderRepository;
import com.dropie.domain.product.entity.Product;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PendingOrderAutoCancelServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private PendingOrderAutoCancelService autoCancelService;

    // 운영에서 Product.event는 NOT NULL FK이므로 테스트에서도 항상 세팅해야 함
    // OPEN 상태 + 미래 endAt → 자동취소 후 SOLD_OUT→OPEN 복귀 분기에 걸리지 않아 재고/취소 검증에만 집중 가능
    private Event openEvent() {
        return Event.builder()
                .brandName("브랜드")
                .status(EventStatus.OPEN)
                .startAt(LocalDateTime.now().minusHours(1))
                .endAt(LocalDateTime.now().plusHours(1))
                .build();
    }

    @Test
    @DisplayName("PENDING 주문 자동 취소 성공 — 상태 CANCELED 전이 + 재고 복구")
    void 자동_취소_성공() {
        // given — stock=0인 상품에 대해 quantity=2가 주문된 상태
        Product product = Product.builder().id(1L).stock(0).event(openEvent()).build();
        OrderItem item = OrderItem.builder().product(product).quantity(2).build();
        Order order = Order.builder()
                .id(100L)
                .status(OrderStatus.PENDING)
                .orderItems(List.of(item))
                .build();

        given(orderRepository.findByIdForUpdate(100L)).willReturn(Optional.of(order));

        // when
        autoCancelService.autoCancelIfPending(100L);

        // then — 주문이 CANCELED로 바뀌고, 재고가 차감분만큼 다시 증가
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(product.getStock()).isEqualTo(2);
    }

    @Test
    @DisplayName("주문 아이템이 여러 개일 때 모든 상품의 재고가 각각 복구된다")
    void 여러_상품_재고_복구() {
        // given
        Product p1 = Product.builder().id(1L).stock(5).event(openEvent()).build();
        Product p2 = Product.builder().id(2L).stock(0).event(openEvent()).build();
        OrderItem i1 = OrderItem.builder().product(p1).quantity(3).build();
        OrderItem i2 = OrderItem.builder().product(p2).quantity(7).build();

        Order order = Order.builder()
                .id(101L)
                .status(OrderStatus.PENDING)
                .orderItems(List.of(i1, i2))
                .build();

        given(orderRepository.findByIdForUpdate(101L)).willReturn(Optional.of(order));

        // when
        autoCancelService.autoCancelIfPending(101L);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(p1.getStock()).isEqualTo(8);  // 5 + 3
        assertThat(p2.getStock()).isEqualTo(7);  // 0 + 7
    }

    @Test
    @DisplayName("이미 PAID 상태면 스킵 — 멱등성 (TTL 만료 직전 결제 완료 케이스)")
    void 이미_PAID면_스킵() {
        // given
        Product product = Product.builder().id(1L).stock(0).event(openEvent()).build();
        OrderItem item = OrderItem.builder().product(product).quantity(2).build();
        Order order = Order.builder()
                .id(100L)
                .status(OrderStatus.PAID)
                .orderItems(List.of(item))
                .build();
        given(orderRepository.findByIdForUpdate(100L)).willReturn(Optional.of(order));

        // when
        autoCancelService.autoCancelIfPending(100L);

        // then — 상태 그대로, 재고 그대로
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(product.getStock()).isEqualTo(0);
    }

    @Test
    @DisplayName("이미 CANCELED 상태면 스킵 — 중복 자동 취소 방지 (배치+리스너 동시 실행 케이스)")
    void 이미_CANCELED면_스킵() {
        // given — Redis 리스너가 한 번 처리한 후 배치가 또 들어오는 케이스
        Product product = Product.builder().id(1L).stock(2).event(openEvent()).build();
        OrderItem item = OrderItem.builder().product(product).quantity(2).build();
        Order order = Order.builder()
                .id(100L)
                .status(OrderStatus.CANCELED)
                .orderItems(List.of(item))
                .build();
        given(orderRepository.findByIdForUpdate(100L)).willReturn(Optional.of(order));

        // when
        autoCancelService.autoCancelIfPending(100L);

        // then — 재고가 한 번 더 증가하면 안 됨 (중복 복구 방지)
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(product.getStock()).isEqualTo(2);
    }

    @Test
    @DisplayName("주문이 없으면 예외 없이 조용히 종료 — 이미 삭제된 주문 케이스")
    void 주문_없으면_무시() {
        // given
        given(orderRepository.findByIdForUpdate(999L)).willReturn(Optional.empty());

        // when & then — 예외 없이 통과해야 함
        assertThatCode(() -> autoCancelService.autoCancelIfPending(999L))
                .doesNotThrowAnyException();

        // 후속 처리(delete/save)가 일어나지 않음을 확인
        then(orderRepository).should(never()).delete(org.mockito.ArgumentMatchers.any());
    }
}
