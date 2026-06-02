package com.dropie.global.config;


import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

// WebSocket + STOMP 구성
//
// 동작 흐름:
//   1) 클라이언트가 /ws-stomp로 SockJS/WebSocket handshake → 연결 수립
//   2) 클라이언트가 SUBSCRIBE /topic/events/{id}/stock → 해당 토픽 구독
//   3) 서버가 SimpMessagingTemplate으로 /topic/...에 메시지 발행
//   4) 브로커가 해당 토픽 구독자 모두에게 전송
@Configuration
@EnableWebSocketMessageBroker
@ConditionalOnProperty(
        name = "dropie.realtime.websocket.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // 브로커 prefix 설정
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 서버가 클라이언트로 보낼 때의 prefix
        // → /topic/...만 사용 (broadcast 패턴)
        // → 추후 1:1 알림이 필요하면 /queue/... 추가
        registry.enableSimpleBroker("/topic");

        // 클라이언트가 서버로 보낼 때의 prefix (이번 기능에선 사용 안 하지만 관례상 등록)
        registry.setApplicationDestinationPrefixes("/app");
    }

    // STOMP 핸드셰이크 엔드포인트
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-stomp")
                // CORS — 운영에선 정확한 도메인으로 제한 필요
                // TODO: 운영 도메인으로 좁힐 것 (예: setAllowedOrigins("https://dropie.app"))
                .setAllowedOriginPatterns("*")
                // SockJS fallback 활성화 (브라우저 호환성)
                .withSockJS();
    }
}
