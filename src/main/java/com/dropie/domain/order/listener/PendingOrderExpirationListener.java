package com.dropie.domain.order.listener;

import com.dropie.domain.order.service.PendingOrderAutoCancelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

// Redis TTL 만료 이벤트를 수신하는 리스너
// → Keyspace Notification으로 발행된 만료 키 이름을 받아서
//   "pending_order:{orderId}" 패턴일 때만 자동 취소 서비스를 호출
@Slf4j
@Component
public class PendingOrderExpirationListener extends KeyExpirationEventMessageListener {

    // 주문 생성 시 사용한 Redis 키 prefix — 다른 TTL 키와 구분하기 위함
    private static final String KEY_PREFIX = "pending_order:";

    private final PendingOrderAutoCancelService autoCancelService;

    public PendingOrderExpirationListener(
            RedisMessageListenerContainer listenerContainer,
            PendingOrderAutoCancelService autoCancelService) {
        // 부모 클래스가 __keyevent@*__:expired 채널을 구독하도록 설정함
        super(listenerContainer);
        this.autoCancelService = autoCancelService;
    }

    /**
     * Redis가 발행한 만료 이벤트를 수신하는 콜백.
     *
     * @param message  만료된 키 이름이 body에 담겨 옴 (예: "pending_order:42")
     * @param pattern  구독 채널 패턴 (사용 안 함)
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = message.toString();

        // 이 리스너는 모든 만료 이벤트를 받기 때문에, 관련 prefix만 필터링
        if (!expiredKey.startsWith(KEY_PREFIX)) {
            return;
        }

        try {
            // "pending_order:42" → "42" 추출
            Long orderId = Long.parseLong(expiredKey.substring(KEY_PREFIX.length()));
            log.info("[PendingOrderExpiration] 만료 이벤트 수신 - orderId={}", orderId);

            // 실제 취소 로직은 별도 서비스에 위임
            // → 리스너는 이벤트 수신/파싱만, 비즈니스 로직은 서비스에서 트랜잭션 안에 실행
            autoCancelService.autoCancelIfPending(orderId);
        } catch (NumberFormatException e) {
            // 키 형식이 예상과 다르면 경고만 남기고 무시
            log.warn("[PendingOrderExpiration] 키 파싱 실패 - key={}", expiredKey);
        } catch (Exception e) {
            // 리스너 안에서 예외가 전파되면 컨테이너가 중단될 수 있음
            // → 개별 주문 처리 실패가 리스너 전체를 죽이지 않도록 catch
            log.error("[PendingOrderExpiration] 자동 취소 처리 실패 - key={}, error={}",
                    expiredKey, e.getMessage(), e);
        }
    }
}
