package com.dropie.domain.address.dto.response;

import com.dropie.domain.address.entity.Address;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

//GET 목록 응답 DTO
@Getter
@Builder
public class AddressResponse {

    private Long id;
    private String receiverName;
    private String phone;
    private String zipcode;
    private String address1;
    private String address2;
    private String label;
    // boolean 필드는 Jackson이 is 접두어를 제거해 "default"로 직렬화되므로 명시적으로 지정
    @JsonProperty("isDefault")
    private boolean isDefault;

    // Entity → DTO 변환은 DTO 안에 팩토리 메서드로 두는 게 일반적
    // Service에서 new AddressResponse(address.getId(), ...) 하지 않아도 됨
    public static AddressResponse from(Address address) {
        return AddressResponse.builder()
                .id(address.getId())
                .receiverName(address.getReceiverName())
                .phone(address.getPhone())
                .zipcode(address.getZipcode())
                .address1(address.getAddress1())
                .address2(address.getAddress2())
                .label(address.getLabel())
                .isDefault(address.isDefault())
                .build();
    }
}
