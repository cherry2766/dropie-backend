package com.dropie.dto.request.address;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

//PATCH 요청 DTO
// PATCH는 변경할 필드만 보내므로 필드가 많고 대부분 null → Builder가 가독성에 유리
@Getter
@Builder
@NoArgsConstructor  // Jackson이 JSON → 객체 변환 시 기본 생성자 필요
@AllArgsConstructor // @Builder와 함께 사용 시 전체 필드 생성자 명시
public class AddressUpdateRequest {

    // PATCH는 변경할 필드만 포함 → 모든 필드 null 허용
    // null이면 "변경 안 함"을 의미하므로 primitive 타입(boolean) 대신 wrapper 타입(Boolean) 사용
    private String receiverName;
    private String phone;
    private String zipcode;
    private String address1;
    private String address2;
    private String label;
    private Boolean isDefault;
}
