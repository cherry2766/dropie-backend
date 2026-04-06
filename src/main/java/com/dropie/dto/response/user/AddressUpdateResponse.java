package com.dropie.dto.response.user;

//PATCH 응답 DTO

import com.dropie.domain.address.Address;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AddressUpdateResponse {

    private Long id;
    private String label;
    private boolean isDefault;

    public static AddressUpdateResponse from(Address address) {
        return AddressUpdateResponse.builder()
                .id(address.getId())
                .label(address.getLabel())
                .isDefault(address.isDefault())
                .build();
    }
}
