package com.dropie.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

// Redis 이벤트 수신을 위한 Listener 컨테이너 설정
// → Keyspace Notification의 __keyevent@*__:expired 채널을 구독할 수 있게 해줌
@Configuration
public class RedisKeyExpirationConfig {

    /**
     * Redis Pub/Sub 메시지를 수신하는 컨테이너.
     * Spring이 내부적으로 Redis 연결을 열고 채널에서 메시지가 오면
     * 등록된 리스너에게 전달한다.
     *
     * @param connectionFactory 이미 등록되어 있는 Redis 연결 팩토리(Spring Boot 자동 구성)
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }
}
