package com.dropie.controller.address;

import com.dropie.dto.request.address.AddressRequest;
import com.dropie.dto.request.address.AddressUpdateRequest;
import com.dropie.dto.response.address.AddressCreateResponse;
import com.dropie.dto.response.address.AddressResponse;
import com.dropie.dto.response.address.AddressUpdateResponse;
import com.dropie.service.address.AddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/users/me/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    // 배송지 목록 조회
    // GET /users/me/addresses
    @GetMapping
    public ResponseEntity<List<AddressResponse>> getAddresses(@AuthenticationPrincipal String email) {
        log.debug("[GET /users/me/addresses] email : {}", email);
        return ResponseEntity.ok(addressService.getAddresses(email)); //200
    }

    // 배송지 추가
    // POST /users/me/addresses
    @PostMapping
    public ResponseEntity<AddressCreateResponse> addAddress(
            @AuthenticationPrincipal String email,
            @RequestBody @Valid AddressRequest request) {
        log.debug("[POST /users/me/addresses] email : {}", email);
        return ResponseEntity.status(HttpStatus.CREATED) //201
                .body(addressService.addAddress(email, request));
    }

    // 배송지 수정
    // PATCH /users/me/addresses/{addressId}
    @PatchMapping("/{addressId}")
    public ResponseEntity<AddressUpdateResponse> updateAddress(
            @AuthenticationPrincipal String email,
            @PathVariable Long addressId,
            @RequestBody AddressUpdateRequest request) {    // PATCH는 모든 필드 optional이라 @Valid 없음
        log.debug("[PATCH /users/me/addresses/{}] email : {}", addressId, email);
        return ResponseEntity.ok(addressService.updateAddress(email, addressId, request));
    }

    // 배송지 삭제
    // DELETE /users/me/addresses/{addressId}
    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> deleteAddress(
            @AuthenticationPrincipal String email,
            @PathVariable Long addressId) {
        log.debug("[DELETE /users/me/addresses/{}] email: {}", addressId, email);
        addressService.deleteAddress(email, addressId);
        return ResponseEntity.noContent().build(); // 204
    }

}
