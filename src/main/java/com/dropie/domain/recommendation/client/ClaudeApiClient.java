package com.dropie.domain.recommendation.client;

import com.dropie.domain.recommendation.dto.request.ClaudeMessageRequest;
import com.dropie.domain.recommendation.dto.response.ClaudeMessageResponse;
import com.dropie.global.config.ClaudeApiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Claude API 호출 클라이언트
 * <p>
 * WebClient 호출과 타임아웃/재시도만 담당하며,
 * 응답을 도메인 모델로 변환하는 책임은 호출부에 위임
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaudeApiClient {

    private final WebClient claudeWebClient;
    private final ClaudeApiProperties properties;

    /**
     * userPrompt 한 덩어리를 보내고 응답 텍스트를 받음
     *
     * @return 응답 텍스트 (실패/타임아웃 시 RuntimeException — 호출부가 폴백 결정)
     */
    public String generate(String userPrompt) {
        ClaudeMessageRequest request = ClaudeMessageRequest.userOnly(
                properties.getModel(),
                properties.getMaxTokens(),
                userPrompt
        );

        try {
            ClaudeMessageResponse response = claudeWebClient.post()
                    .uri("/v1/messages")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ClaudeMessageResponse.class)
                    .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .block(); // 응답 올 때까지 대기

            if (response == null) {
                throw new IllegalStateException("Claude API empty response");
            }

            String text = response.extractText();
            if (text.isBlank()) {
                throw new IllegalStateException("Claude API returned blank text");
            }

            log.debug("[ClaudeApiClient] OK - len={}", text.length());
            return text;

        } catch (Exception e) {
            // 4xx/5xx, 타임아웃, 직렬화 오류 등 모두 여기로
            log.warn("[ClaudeApiClient] 호출 실패 - msg={}", e.getMessage());
            throw new RuntimeException("Claude API 호출 실패", e);
        }
    }
}
