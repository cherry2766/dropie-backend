package com.dropie.domain.recommendation.client;


import com.dropie.global.config.ClaudeApiProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaudeApiClientTest {

    private MockWebServer server;
    private ClaudeApiClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        ClaudeApiProperties props = new ClaudeApiProperties();
        props.setKey("test-key");
        props.setBaseUrl(server.url("/").toString());
        props.setModel("claude-haiku-4-5-20251001");
        props.setMaxTokens(200);
        props.setTimeoutMs(2000);

        WebClient webClient = WebClient.builder().baseUrl(props.getBaseUrl()).build();
        client = new ClaudeApiClient(webClient, props);
    }

    @AfterEach
    void tearDown() throws IOException { server.shutdown(); }

    @Test
    @DisplayName("정상 응답 — content[0].text를 그대로 반환")
    void 정상_응답() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                    { "id": "msg_123",
                      "content": [{"type": "text", "text": "포근한 오후에 어울리는 디저트예요."}],
                      "stop_reason": "end_turn" }
                    """));
        assertThat(client.generate("프롬프트")).isEqualTo("포근한 오후에 어울리는 디저트예요.");
    }

    @Test
    @DisplayName("빈 content — RuntimeException")
    void 빈_content_예외() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"id":"msg_123","content":[],"stop_reason":"end_turn"}"""));
        assertThatThrownBy(() -> client.generate("프롬프트"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("서버 5xx — RuntimeException")
    void 서버_500_예외() {
        server.enqueue(new MockResponse().setResponseCode(500));
        assertThatThrownBy(() -> client.generate("프롬프트"))
                .isInstanceOf(RuntimeException.class);
    }
}