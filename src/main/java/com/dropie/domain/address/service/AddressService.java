package com.dropie.domain.address.service;

import com.dropie.domain.address.entity.Address;
import com.dropie.domain.user.entity.User;
import com.dropie.domain.address.dto.request.AddressRequest;
import com.dropie.domain.address.dto.request.AddressUpdateRequest;
import com.dropie.domain.address.dto.response.AddressCreateResponse;
import com.dropie.domain.address.dto.response.AddressResponse;
import com.dropie.domain.address.dto.response.AddressUpdateResponse;
import com.dropie.global.exception.custom.AddressNotFoundException;
import com.dropie.global.exception.custom.UserNotFoundException;
import com.dropie.domain.address.repository.AddressRepository;
import com.dropie.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    // 배송지 목록 조회
    @Transactional(readOnly = true)
    public List<AddressResponse> getAddresses(String email) {
        log.debug("[getAddresses] 조회 요청 - email: {}", email);

        User user = getUserByEmail(email);
        List<AddressResponse> result = addressRepository.findAllByUser(user)
                .stream()
                .map(AddressResponse::from)
                .toList();

        log.info("[getAddresses] 조회 완료 - userId: {}, 배송지 수: {}", user.getId(), result.size());
        return result;
    }

    // 배송지 추가
    @Transactional
    public AddressCreateResponse addAddress(String email, AddressRequest request) {
        log.debug("[addAddress] 추가 요청 - email: {}", email);

        User user = getUserByEmail(email);

        List<Address> existing = addressRepository.findAllByUser(user);
        boolean isFirst = existing.isEmpty();

        // 첫 배송지 → 요청값 무시하고 무조건 기본 배송지로 지정
        // 첫 배송지가 아닌데 isDefault=true → 기존 기본 배송지 해제 후 지정
        // 첫 배송지가 아닌데 isDefault=false → 그냥 추가
        // request.getIsDefault() -> 사용자가 기본 배송지로 설정한 경우 true
        boolean setAsDefault = isFirst || request.getIsDefault();

        if (setAsDefault && !isFirst) {
            // 기존 기본 배송지가 있는 경우에만 해제
            unsetCurrentDefault(user);
        }

        Address address = Address.builder()
                .user(user)
                .receiverName(request.getReceiverName())
                .phone(request.getPhone())
                .zipcode(request.getZipcode())
                .address1(request.getAddress1())
                .address2(request.getAddress2())
                .label(request.getLabel())
                .isDefault(setAsDefault)
                .build();

        AddressCreateResponse response = AddressCreateResponse.from(addressRepository.save(address));
        log.info("[addAddress] 추가 완료 - userId: {}, addressId: {}", user.getId(), response.getId());
        return response;
    }

    // 배송지 수정
    @Transactional
    public AddressUpdateResponse updateAddress(String email, Long addressId, AddressUpdateRequest request) {
        log.debug("[updateAddress] 수정 요청 - email: {}, addressId: {}", email, addressId);

        User user = getUserByEmail(email);

        // 본인 배송지인지 확인 — findByIdAndUser로 user 조건까지 같이 검사
        Address address = addressRepository.findByIdAndUser(addressId, user)
                .orElseThrow(() -> {
                    log.warn("[updateAddress] 배송지 없음 - addressId: {}", addressId);
                    return new AddressNotFoundException();
                });

        // isDefault를 true로 바꾸는 경우에만 기존 기본 배송지 해제
        // false로 바꾸거나 null(변경 안 함)이면 해제하지 않음
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            unsetCurrentDefault(user);
        }

        address.update(
                request.getReceiverName(),
                request.getPhone(),
                request.getZipcode(),
                request.getAddress1(),
                request.getAddress2(),
                request.getLabel(),
                request.getIsDefault()
        );
        // @Transactional이므로 별도 save() 없이 dirty checking으로 자동 반영

        log.info("[updateAddress] 수정 완료 - addressId: {}", addressId);
        return AddressUpdateResponse.from(address);
    }

    // 배송지 삭제
    @Transactional
    public void deleteAddress(String email, Long addressId) {
        log.debug("[deleteAddress] 삭제 요청 - email: {}, addressId: {}", email, addressId);

        User user = getUserByEmail(email);

        Address address = addressRepository.findByIdAndUser(addressId, user)
                .orElseThrow(() -> {
                    log.warn("[deleteAddress] 배송지 없음 - addressId: {}", addressId);
                    return new AddressNotFoundException();
                });

        boolean wasDefault = address.isDefault();
        addressRepository.delete(address);
        log.info("[deleteAddress] 삭제 완료 - addressId: {}, wasDefault: {}", addressId, wasDefault);

        // 기본 배송지를 삭제한 경우 → 남은 배송지 중 첫 번째를 새 기본 배송지로 지정
        if (wasDefault) {
            addressRepository.findAllByUser(user)
                    .stream()
                    .findFirst()
                    .ifPresent(Address::setDefaultAddress);
        }
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("[getUserByEmail] 유저 없음 - email: {}", email);
                    return new UserNotFoundException();
                });
    }

    // 현재 기본 배송지를 해제 — 기본 배송지는 항상 1개이므로 Optional로 처리
    // 이 유저의 기본 배송지가 존재하면 isDefault를 false로 변경
    private void unsetCurrentDefault(User user) {
        addressRepository.findByUserAndIsDefaultTrue(user)
                .ifPresent(Address::clearDefault);
    }
}
