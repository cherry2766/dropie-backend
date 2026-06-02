package com.dropie.global.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    // RedissonClient: 분산 락 전용 Redis 클라이언트
    // RedisTemplate(캐싱, 랭킹 등 일반 데이터)과 역할을 분리하는 이유:
    // 커넥션 풀 크기, 직렬화 방식 등 각 용도에 맞는 설정을 독립적으로 관리할 수 있기 때문
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        // Redisson은 반드시 "redis://" 접두사 형식의 주소를 요구함
        config.useSingleServer()
                .setAddress("redis://" + redisHost + ":" + redisPort)
                .setConnectionPoolSize(10)          // 커넥션 풀 크기 (기본값 64 → 소규모 서비스에 맞게 조정)
                .setConnectionMinimumIdleSize(5);   // 최소 유지 커넥션 수 (갑작스런 요청에 즉시 대응)
        return Redisson.create(config);
    }
}
