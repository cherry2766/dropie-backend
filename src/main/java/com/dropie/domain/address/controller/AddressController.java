package com.dropie.domain.address.controller;

import com.dropie.domain.address.dto.request.AddressRequest;
import com.dropie.domain.address.dto.request.AddressUpdateRequest;
import com.dropie.domain.address.dto.response.AddressCreateResponse;
import com.dropie.domain.address.dto.response.AddressResponse;
import com.dropie.domain.address.dto.response.AddressUpdateResponse;
import com.dropie.domain.address.service.AddressService;
import com.dropie.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "배송지", description = "사용자 배송지 목록·추가·수정·삭제 (기본 배송지 1건 보장)")
@Slf4j
@RestController
@RequestMapping("/users/me/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    // 배송지 목록 조회
    // GET /users/me/addresses
    @GetMapping
    public ResponseEntity<List<AddressResponse>> getAddresses(@AuthenticationPrincipal CustomUserDetails userDetails) {
        log.debug("[GET /users/me/addresses] email : {}", userDetails.getUsername());
        return ResponseEntity.ok(addressService.getAddresses(userDetails.getUsername())); //200
    }

    // 배송지 추가
    // POST /users/me/addresses
    @PostMapping
    public ResponseEntity<AddressCreateResponse> addAddress(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody @Valid AddressRequest request) {
        log.debug("[POST /users/me/addresses] email : {}", userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED) //201
                .body(addressService.addAddress(userDetails.getUsername(), request));
    }

    // 배송지 수정
    // PATCH /users/me/addresses/{addressId}
    @PatchMapping("/{addressId}")
    public ResponseEntity<AddressUpdateResponse> updateAddress(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long addressId,
            @RequestBody AddressUpdateRequest request) {    // PATCH는 모든 필드 optional이라 @Valid 없음
        log.debug("[PATCH /users/me/addresses/{}] email : {}", addressId, userDetails.getUsername());
        return ResponseEntity.ok(addressService.updateAddress(userDetails.getUsername(), addressId, request));
    }

    // 배송지 삭제
    // DELETE /users/me/addresses/{addressId}
    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> deleteAddress(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long addressId) {
        log.debug("[DELETE /users/me/addresses/{}] email: {}", addressId, userDetails.getUsername());
        addressService.deleteAddress(userDetails.getUsername(), addressId);
        return ResponseEntity.noContent().build(); // 204
    }

}
