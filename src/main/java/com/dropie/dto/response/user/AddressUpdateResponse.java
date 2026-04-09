package com.dropie.dto.response.user;

//PATCH 응답 DTO

import com.dropie.domain.address.Address;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AddressUpdateResponse {

    private Long id;
    private String label;
    // boolean 필드는 Jackson이 is 접두어를 제거해 "default"로 직렬화되므로 명시적으로 지정
    @JsonProperty("isDefault")
    private boolean isDefault;

    public static AddressUpdateResponse from(Address address) {
        return AddressUpdateResponse.builder()
                .id(address.getId())
                .label(address.getLabel())
                .isDefault(address.isDefault())
                .build();
    }
}
