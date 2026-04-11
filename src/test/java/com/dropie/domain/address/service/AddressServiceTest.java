package com.dropie.domain.address.service;

import com.dropie.domain.address.entity.Address;
import com.dropie.domain.address.dto.request.AddressRequest;
import com.dropie.domain.address.dto.request.AddressUpdateRequest;
import com.dropie.domain.address.dto.response.AddressCreateResponse;
import com.dropie.domain.address.dto.response.AddressResponse;
import com.dropie.domain.address.dto.response.AddressUpdateResponse;
import com.dropie.domain.address.repository.AddressRepository;
import com.dropie.domain.user.entity.Role;
import com.dropie.domain.user.entity.User;
import com.dropie.domain.user.repository.UserRepository;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

// Spring Context를 띄우지 않고 Mockito만으로 서비스 로직 단위 테스트
// → 빠르고 DB/외부 의존성 없이 순수 비즈니스 로직만 검증
@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    // 테스트 대상 - @Mock들이 자동 주입됨
    @InjectMocks
    private AddressService addressService;

    // @Mock : 실제 DB 대신 가짜 객체로 대체
    @Mock
    private AddressRepository addressRepository;

    // @Mock : 실제 DB 대신 가짜 객체로 대체
    @Mock
    private UserRepository userRepository;

    private User user;
    private Address defaultAddress;
    private Address nonDefaultAddress;

    // @BeforeEach : 각 @Test 실행 전마다 새로 초기화됨 (테스트 간 상태 오염 방지)
    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("test@email.com")
                .password("pwd1234")
                .nickname("체리콩")
                .role(Role.USER)
                .build();
        // 기본 배송지
        defaultAddress = Address.builder()
                .user(user)
                .receiverName("체리")
                .phone("010-1234-5678")
                .zipcode("12345")
                .address1("경기도 파주시")
                .address2("101호")
                .label("집")
                .isDefault(true)
                .build();
        // 일반 배송지
        nonDefaultAddress = Address.builder()
                .user(user)
                .receiverName("포도")
                .phone("010-9876-5432")
                .zipcode("54321")
                .address1("서울시 마포구")
                .address2(null)
                .label("회사")
                .isDefault(false)
                .build();
    }

    @Test
    @DisplayName("배송지 목록 조회 성공")
    void 배송지_목록조회_성공() {
        // given
        given(userRepository.findByEmail("test@email.com"))
                .willReturn(Optional.of(user));
        given(addressRepository.findAllByUser(user))
                .willReturn(List.of(defaultAddress, nonDefaultAddress));

        // when
        List<AddressResponse> result = addressService.getAddresses("test@email.com");

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).isDefault()).isTrue();
    }

    @Test
    @DisplayName("배송지 목록 조회 - 없으면 빈 리스트 반환")
    void 배송지_목록조회_빈리스트() {
        // given
        given(userRepository.findByEmail("test@email.com"))
                .willReturn(Optional.of(user));
        given(addressRepository.findAllByUser(user))
                .willReturn(List.of());

        // when
        List<AddressResponse> result = addressService.getAddresses("test@email.com");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("배송지 목록 조회 실패 - 유저 없음")
    void 배송지_목록조회_유저없음_예외() {
        // given
        given(userRepository.findByEmail("test2@email.com"))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                addressService.getAddresses("test2@email.com"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("배송지 추가 성공 - 첫 번째는 무조건 기본 배송지")
    void 배송지_추가_첫번째_자동기본지정() {
        // given
        // isDefault=false로 요청했지만, 기존 배송지가 없으면 서비스 로직상 강제로 true가 됨
        AddressRequest request = new AddressRequest("강체리", "010-1111-2222", "12345", "경기도 고양시", "101호", "집", false);

        given(userRepository.findByEmail("test@email.com"))
                .willReturn(Optional.of(user));
        // 기존 배송지 없음 → isFirst = true 분기
        given(addressRepository.findAllByUser(user))
                .willReturn(List.of());
        // save()가 호출될 때 defaultAddress(isDefault=true)를 리턴
        given(addressRepository.save(any()))
                .willReturn(defaultAddress);

        // when
        AddressCreateResponse response = addressService.addAddress("test@email.com", request);

        // then
        // 요청은 false였지만 첫 배송지이므로 true여야 함
        assertThat(response.isDefault()).isTrue();
        then(addressRepository).should().save(any(Address.class));
    }

    @Test
    @DisplayName("배송지 추가 성공 - isDefault=true 요청 시 기존 기본 배송지 해제 후 새 배송지를 기본으로")
    void 배송지_추가_기본배송지_교체() {
        // given
        AddressRequest request = new AddressRequest("딸기", "010-0000-0000", "54321", "서울시 마포구", null, null, true);

        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));
        // 기존 배송지 있음 → isFirst = false
        given(addressRepository.findAllByUser(user)).willReturn(List.of(defaultAddress));
        // 현재 기본 배송지가 defaultAddress
        given(addressRepository.findByUserAndIsDefaultTrue(user)).willReturn(Optional.of(defaultAddress));
        given(addressRepository.save(any(Address.class))).willReturn(nonDefaultAddress);

        // when
        addressService.addAddress("test@email.com", request);

        // then
        // 기존 기본 배송지에 clearDefault()가 호출돼 isDefault가 false로 바뀌어야 함
        assertThat(defaultAddress.isDefault()).isFalse();
        then(addressRepository).should().save(any(Address.class));
    }

    @Test
    @DisplayName("배송지 추가 성공 - isDefault=false 요청 시 기존 기본 배송지 유지")
    void 배송지_추가_비기본_기존기본유지() {
        // given
        AddressRequest request = new AddressRequest("딸기", "010-0000-0000", "54321", "서울시 마포구", null, null, false);

        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));
        given(addressRepository.findAllByUser(user)).willReturn(List.of(defaultAddress));
        given(addressRepository.save(any(Address.class))).willReturn(nonDefaultAddress);

        // when
        addressService.addAddress("test@email.com", request);

        // then
        // isDefault=false + 첫 배송지 아님 → unsetCurrentDefault() 호출 안 됨
        // 즉, findByUserAndIsDefaultTrue 가 호출되지 않아야 함
        then(addressRepository).should(never()).findByUserAndIsDefaultTrue(any());
    }

    @Test
    @DisplayName("배송지 수정 성공 - 일부 필드만 변경")
    void 배송지_수정_성공() {
        // given
        // phone만 바꾸는 부분 업데이트 — PATCH라서 나머지 필드는 null (변경 안 함)
        // Builder 패턴으로 변경할 필드만 명시 → 가독성 우수
        AddressUpdateRequest request = AddressUpdateRequest.builder()
                .phone("010-0000-0000")
                .build();

        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));
        // findByIdAndUser : 본인 배송지인지 user 조건까지 함께 검사
        given(addressRepository.findByIdAndUser(1L, user)).willReturn(Optional.of(defaultAddress));

        // when
        AddressUpdateResponse response = addressService.updateAddress("test@email.com", 1L, request);

        // then
        // @Transactional dirty checking으로 save() 없이 자동 반영
        // phone이 실제로 변경됐는지 확인
        assertThat(defaultAddress.getPhone()).isEqualTo("010-0000-0000");
    }

    @Test
    @DisplayName("배송지 수정 성공 - isDefault=true로 변경 시 기존 기본 배송지 해제")
    void 배송지_수정_기본배송지_변경() {
        // given
        // nonDefaultAddress를 기본 배송지로 변경하는 요청
        AddressUpdateRequest request = AddressUpdateRequest.builder()
                .isDefault(true)
                .build();

        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));
        given(addressRepository.findByIdAndUser(1L, user)).willReturn(Optional.of(nonDefaultAddress));
        // 기존 기본 배송지가 defaultAddress
        given(addressRepository.findByUserAndIsDefaultTrue(user)).willReturn(Optional.of(defaultAddress));

        // when
        addressService.updateAddress("test@email.com", 1L, request);

        // then
        // 기존 기본 배송지 해제됐는지 확인
        assertThat(defaultAddress.isDefault()).isFalse();
        // 수정 대상이 기본 배송지로 지정됐는지 확인
        assertThat(nonDefaultAddress.isDefault()).isTrue();
    }

    @Test
    @DisplayName("배송지 수정 성공 - isDefault=null이면 기본 배송지 변경 없음")
    void 배송지_수정_기본배송지_변경없음() {
        // given
        // isDefault를 builder에서 세팅하지 않으면 null → 변경 안 함
        AddressUpdateRequest request = AddressUpdateRequest.builder()
                .label("가게")
                .build();

        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));
        given(addressRepository.findByIdAndUser(1L, user)).willReturn(Optional.of(nonDefaultAddress));

        // when
        addressService.updateAddress("test@email.com", 1L, request);

        // then
        // isDefault=null → Boolean.TRUE.equals(null) = false → unsetCurrentDefault() 호출 안 됨
        then(addressRepository).should(never()).findByUserAndIsDefaultTrue(any());
    }

    @Test
    @DisplayName("배송지 수정 실패 - 본인 배송지가 아님")
    void 배송지_수정_배송지없음_예외() {
        // given
        AddressUpdateRequest request = AddressUpdateRequest.builder()
                .phone("010-1111-2222")
                .build();

        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));
        // findByIdAndUser가 빈 Optional → 해당 ID가 없거나 다른 유저의 배송지
        given(addressRepository.findByIdAndUser(999L, user)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> addressService.updateAddress("test@email.com", 999L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ADDRESS_NOT_FOUND);
    }

    @Test
    @DisplayName("배송지 삭제 성공 - 일반 배송지 삭제 시 기본 배송지 자동 지정 없음")
    void 배송지_삭제_비기본_성공() {
        // given
        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));
        // 삭제 대상이 기본 배송지가 아님
        given(addressRepository.findByIdAndUser(2L, user)).willReturn(Optional.of(nonDefaultAddress));

        // when
        addressService.deleteAddress("test@email.com", 2L);

        // then
        then(addressRepository).should().delete(nonDefaultAddress);
        // wasDefault = false → 이후 재지정 로직(findAllByUser) 호출 안 됨
        then(addressRepository).should(never()).findAllByUser(any());
    }

    @Test
    @DisplayName("배송지 삭제 성공 - 기본 배송지 삭제 시 남은 첫 번째 배송지가 기본으로 자동 지정")
    void 배송지_삭제_기본배송지_삭제후_자동재지정() {
        // given
        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));
        // 삭제 대상이 기본 배송지
        given(addressRepository.findByIdAndUser(1L, user)).willReturn(Optional.of(defaultAddress));
        // 삭제 후 남은 배송지 목록 — nonDefaultAddress가 첫 번째
        given(addressRepository.findAllByUser(user)).willReturn(List.of(nonDefaultAddress));

        // when
        addressService.deleteAddress("test@email.com", 1L);

        // then
        then(addressRepository).should().delete(defaultAddress);
        // 남은 첫 번째 배송지에 setDefaultAddress()가 호출돼 isDefault = true로 바뀌어야 함
        assertThat(nonDefaultAddress.isDefault()).isTrue();
    }

    @Test
    @DisplayName("배송지 삭제 성공 - 기본 배송지이자 마지막 배송지 삭제 시 자동 지정 없음")
    void 배송지_삭제_기본배송지_마지막_삭제() {
        // given
        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));
        given(addressRepository.findByIdAndUser(1L, user)).willReturn(Optional.of(defaultAddress));
        // 삭제 후 남은 배송지 없음
        given(addressRepository.findAllByUser(user)).willReturn(List.of());

        // when
        addressService.deleteAddress("test@email.com", 1L);

        // then
        then(addressRepository).should().delete(defaultAddress);
        // 남은 배송지 없음 → 기본 배송지 재지정 없음 → save() 호출 안 됨
        then(addressRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("배송지 삭제 실패 - 본인 배송지가 아님")
    void 배송지_삭제_배송지없음_예외() {
        // given
        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));
        given(addressRepository.findByIdAndUser(999L, user)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> addressService.deleteAddress("test@email.com", 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ADDRESS_NOT_FOUND);
    }
}
