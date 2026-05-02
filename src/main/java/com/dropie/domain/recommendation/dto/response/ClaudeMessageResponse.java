package com.dropie.domain.recommendation.dto.response;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

// Claude Messages API 응답 형식 (필요한 필드만)
//   {
//     "id": "...",
//     "content": [{"type": "text", "text": "추천 문구"}],
//     "stop_reason": "end_turn",
//     ...
//   }
//
// 모르는 필드가 와도 무시되도록 @JsonIgnoreProperties(ignoreUnknown = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaudeMessageResponse(
        String id,
        List<ContentBlock> content,
        String stop_reason
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentBlock(String type, String text) {}

    /**
     * content 블록을 합쳐 텍스트 한 덩어리로 반환
     * - 보통 type="text" 블록 하나만 옴
     * - 비어있는 응답에 대비해 null/빈 문자열 처리
     */
    public String extractText() {
        if (content == null || content.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : content) {
            if ("text".equals(block.type()) && block.text() != null) {
                sb.append(block.text());
            }
        }
        return sb.toString().trim();
    }
}
