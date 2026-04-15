package com.dropie.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),                       // 400 - @Valid 검사 실패 시 사용, 필드 단위 오류는 ErrorResponse에서 별도 처리
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),    // 401 - 어떤 항목이 틀렸는지 노출하지 않기 위해 메시지를 하나로 통일
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),                            // 401 - 토큰 자체가 없을 때, INVALID_CREDENTIALS와 구분
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),                                 // 403 - 토큰은 있지만 권한이 없을 때

    // JWT
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),                      // 401 - 서명 불일치, 형식 오류 등
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),                             // 401 - INVALID_TOKEN과 구분해서 클라이언트가 재발급 요청 여부 판단 가능

    // 유저
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),                       // 404
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),                      // 409 - 회원가입 시 이메일 중복
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),                   // 409 - 닉네임 중복

    // 이벤트
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 이벤트입니다."),                      // 404
    EVENT_NOT_STARTED(HttpStatus.BAD_REQUEST, "아직 판매 시작 전입니다."),                    // 400 - 시작 전/종료 후를 구분해서 클라이언트가 다르게 대응할 수 있게 분리
    EVENT_ENDED(HttpStatus.BAD_REQUEST, "판매가 종료되었습니다."),                            // 400 - 위와 동일한 이유로 NOT_STARTED와 별도 코드 사용
    INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "유효하지 않은 상태 전환입니다."),       // 400 - 허용되지 않는 상태 변경 시도

    // 상품
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 상품입니다."),                      // 404
    OUT_OF_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다."),                                  // 409 - 재고 부족은 입력값 문제가 아닌 서버 상태 충돌이므로 400 대신 409 사용
    INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "잘못된 수량 요청입니다."),                      // 400 - 수량 자체가 0 이하로 잘못된 입력값이므로 400 사용 (OUT_OF_STOCK과 구분)

    // 주문
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 주문입니다."),                       // 404
    ORDER_TIME_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "주문 가능한 시간이 아닙니다."),           // 400 - 이벤트 오픈 시간 외 주문 시도
    CANCEL_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "취소할 수 없는 주문 상태입니다."),            // 400 - CANCELED/COMPLETED 상태일 때만 발생, 상태 검증은 Order.cancel()에서 처리
    DUPLICATE_ORDER_ITEM(HttpStatus.BAD_REQUEST, "동일한 상품을 중복 요청할 수 없습니다."),  // 400 - 같은 productId가 items에 두 번 이상 포함된 경우

    // 태그
    TAG_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 태그입니다."),                         // 404

    // 배송지
    ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 배송지입니다."),                   // 404

    // 동시성 제어
    // 503: 락 획득 실패는 입력값 문제가 아니라 서버 부하 상황이므로 SERVICE_UNAVAILABLE 사용
    //      클라이언트가 "잠시 후 재시도"하도록 유도
    LOCK_ACQUISITION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "현재 요청이 많습니다. 잠시 후 다시 시도해주세요."),

    // 409: 낙관적 락 재시도 3회 소진 — 재고는 있지만 경쟁에서 계속 밀린 상황
    ORDER_CONFLICT(HttpStatus.CONFLICT, "주문 처리 중 문제가 발생했습니다. 다시 시도해주세요.");

    private final HttpStatus status;
    private final String message;
}
