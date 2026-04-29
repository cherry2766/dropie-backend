package com.dropie.domain.event.listener;


import com.dropie.domain.event.dto.response.StockUpdateMessage;
import com.dropie.domain.event.event.StockChangedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class StockChangedEventListenerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private StockChangedEventListener listener;

    @Test
    @DisplayName("재고 변경 이벤트가 들어오면 /topic/events/{id}/stock 으로 메시지가 전송된다")
    void 메시지_전송() {
        // given
        StockChangedEvent event = StockChangedEvent.of(1L, 100L, 27);

        // when
        listener.onStockChanged(event);

        // then
        then(messagingTemplate).should().convertAndSend(
                eq("/topic/events/1/stock"),
                any(StockUpdateMessage.class)
        );
    }
}