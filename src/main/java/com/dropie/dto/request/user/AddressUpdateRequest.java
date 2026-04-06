package com.dropie.dto.request.user;

import lombok.Getter;

//PATCH 요청 DTO
@Getter
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
