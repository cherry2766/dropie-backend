package com.dropie.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

// @EnableRetry: Spring Retry 기능을 활성화하는 설정
// @Retryable 어노테이션이 동작하려면 반드시 이 설정이 있어야 함
// 이유: @Retryable은 AOP 방식으로 동작하기 때문에 Spring 컨텍스트에서 Retry 기능을 명시적으로 켜줘야 함
@Configuration
@EnableRetry
public class AppConfig {
}
