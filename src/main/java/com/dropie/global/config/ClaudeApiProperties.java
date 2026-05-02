package com.dropie.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

// claude.api.* 프로퍼티 매핑
//   - 한 곳에 모아두면 IDE 자동완성/리팩토링이 안전
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "claude.api")
public class ClaudeApiProperties {
    private String key;
    private String baseUrl;
    private String model;
    private long timeoutMs;
    private int maxTokens;
}
