package com.dropie.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(
        info = @Info(
                title = "Dropie API",
                version = "1.0",
                description = """
            디저트 드롭 이벤트 플랫폼 — 동시성 제어와 AI 개인화 추천이 핵심

            **주요 설계 포인트**
            - Redisson 분산락(MultiLock + 데드락 방지 정렬)으로 선착순 주문 동시성 제어 (낙관적 락 구현체도 토글 가능)
            - Redis ZSET 기반 취향 집계 + Claude API로 개인화 문구 생성
            - AFTER_COMMIT + @Async로 외부 API를 결제 트랜잭션에서 완전 분리
            """
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Local")
        }
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
@Configuration
public class OpenApiConfig {
}
