package com.dropie.global.common;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

// 공통 페이지네이션 응답 래퍼
// Spring의 Page 객체를 그대로 반환하면 불필요한 필드가 너무 많이 노출되므로
// 필요한 필드만 골라서 커스텀 응답으로 감싸기
// 제네릭 T를 사용해서 어떤 타입이든 재사용 가능
@Getter
@Builder
public class PageResponse<T> {

    private List<T> content;
    private int page;           // 현재 페이지
    private int size;           // 페이지 크기
    private long totalElements; // 전체 데이터 수
    private int totalPages;     // 전체 페이지 수

    // Page<T> → PageResponse<T> 변환
    // Spring의 Page는 0-based이므로 +1 해서 1-based로 변환
    public static <T> PageResponse<T> from(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber() + 1)
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}
