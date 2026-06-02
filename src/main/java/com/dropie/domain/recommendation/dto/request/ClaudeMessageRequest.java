package com.dropie.domain.recommendation.dto.request;

import java.util.List;

// Claude Messages API 요청 형식
//   POST {baseUrl}/v1/messages
//   {
//     "model": "...",
//     "max_tokens": 200,
//     "messages": [{"role": "user", "content": "프롬프트 내용"}]
//   }
public record ClaudeMessageRequest(
        String model,
        int max_tokens,
        List<Message> messages
) {
    public record Message(String role, String content) {

    }

    public static ClaudeMessageRequest userOnly(String model, int maxTokens, String userPrompt) {
        return new ClaudeMessageRequest(
                model,
                maxTokens,
                List.of(new Message("user", userPrompt))
        );
    }
}
