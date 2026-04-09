package com.dropie.dto.request.address;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

//POST 요청 DTO
@Getter
@NoArgsConstructor  // Jackson이 JSON → 객체 변환 시 기본 생성자 필요
@AllArgsConstructor // 테스트에서 생성자로 객체 생성 시 사용
public class AddressRequest {

    @NotBlank
    private String receiverName;    //받는 사람 - 필수

    @NotBlank
    private String phone;   //전화 번호 - 필수

    @NotBlank
    private String zipcode;    //우편 번호 - 필수

    @NotBlank
    private String address1;    //기본주소 - 필수

    private String address2;    //상세주소 - 선택

    private String label;   //(집, 회사) - 선택

    @NotNull
    private Boolean isDefault;   //기본 배송지 여부
}
