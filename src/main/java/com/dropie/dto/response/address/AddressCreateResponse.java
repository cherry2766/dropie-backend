package com.dropie.dto.response.address;

import com.dropie.domain.address.Address;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

//POST 응답 DTO
@Getter
@Builder
public class AddressCreateResponse {

    // POST 응답은 전체 필드가 아닌 핵심 정보만 반환
    private Long id;
    private String receiverName;
    // boolean 필드는 Jackson이 is 접두어를 제거해 "default"로 직렬화되므로 명시적으로 지정
    @JsonProperty("isDefault")
    private boolean isDefault;

    public static AddressCreateResponse from(Address address) {
        return AddressCreateResponse.builder()
                .id(address.getId())
                .receiverName(address.getReceiverName())
                .isDefault(address.isDefault())
                .build();
    }
}
