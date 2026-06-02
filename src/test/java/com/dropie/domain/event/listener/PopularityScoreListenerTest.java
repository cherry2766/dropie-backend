package com.dropie.domain.event.listener;

import com.dropie.domain.event.service.PopularEventService;
import com.dropie.domain.order.event.OrderPaidEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PopularityScoreListenerTest {

    @Mock
    private PopularEventService popularEventService;

    @InjectMocks
    private PopularityScoreListener listener;

    @Test
    @DisplayName("OrderPaidEvent를 받으면 ORDER_SCORE만큼 누적 호출")
    void 결제_완료_시_점수_누적() {
        OrderPaidEvent event = new OrderPaidEvent(100L, 1L, 12L);

        listener.onOrderPaid(event);

        then(popularEventService).should()
                .addScore(eq(12L), eq(PopularEventService.ORDER_SCORE));
    }
}