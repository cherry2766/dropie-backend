package com.dropie.domain.event.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class LineupRoundResponse {

    private int round;  // 차수 번호 (1차, 2차, 3차 등)
    private String status;  // 이벤트 상태 (FINISHED / OPEN / UPCOMING)
    private List<String> brands;    // 해당 차수의 브랜드명 목록
}
