package com.dropie.controller.address;

import com.dropie.config.SecurityConfig;
import com.dropie.dto.response.address.AddressCreateResponse;
import com.dropie.dto.response.address.AddressResponse;
import com.dropie.dto.response.address.AddressUpdateResponse;
import com.dropie.exception.BusinessException;
import com.dropie.exception.ErrorCode;
import com.dropie.security.JwtTokenProvider;
import com.dropie.service.address.AddressService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// AddressController 레이어만 Spring에 올림
// Service는 @MockitoBean으로 대체 → 실제 비즈니스 로직 실행 없이 HTTP 동작만 검증
@WebMvcTest(AddressController.class)
// SecurityConfig 명시적 로드 → csrf disable, 인증 필터 등 Security 설정 적용
@Import(SecurityConfig.class)
class AddressControllerTest {

    // 실제 HTTP 서버 없이 컨트롤러에 가상 요청을 보내는 도구
    @Autowired
    private MockMvc mockMvc;

    // 실제 Service 대신 Mock으로 대체 — 원하는 응답을 직접 지정
    @MockitoBean
    private AddressService addressService;

    // SecurityConfig가 로드될 때 JwtTokenProvider 빈이 필요함
    // 실제 JWT 동작은 필요 없고 빈 등록만 되면 되므로 Mock으로 대체
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("GET /users/me/addresses - 성공 시 200과 배송지 목록 반환")
    @WithMockUser // @WithMockUser → 가짜 인증 사용자로 요청 처리 (email은 "user"로 주입됨)
    void 배송지_목록조회_API_성공() throws Exception {
        // given
        AddressResponse response = AddressResponse.builder()
                .id(1L)
                .receiverName("체리")
                .phone("010-1234-5678")
                .zipcode("12345")
                .address1("서울시 강남구")
                .isDefault(true)
                .build();

        given(addressService.getAddresses(any())).willReturn(List.of(response));

        // when & then
        mockMvc.perform(get("/users/me/addresses"))
                .andExpect(status().isOk()) // 200
                .andExpect(jsonPath("$[0].receiverName").value("체리"))   // 첫 번째 항목 이름
                .andExpect(jsonPath("$[0].isDefault").value(true));      // 기본 배송지 여부
    }

    @Test
    @DisplayName("GET /users/me/addresses - 미인증 시 401")
    void 배송지_목록조회_API_미인증() throws Exception {
        // @WithMockUser 없음 → Spring Security가 인증 실패로 401 반환
        mockMvc.perform(get("/users/me/addresses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /users/me/addresses - 성공 시 201과 생성된 배송지 반환")
    @WithMockUser
    void 배송지_추가_API_성공() throws Exception {
        // given
        AddressCreateResponse response = AddressCreateResponse.builder()
                .id(1L)
                .receiverName("체리")
                .isDefault(true)
                .build();

        given(addressService.addAddress(any(), any())).willReturn(response);

        // when & then
        mockMvc.perform(post("/users/me/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "receiverName": "체리",
                                    "phone": "010-1234-5678",
                                    "zipcode": "12345",
                                    "address1": "서울시 강남구",
                                    "address2": "101호",
                                    "label": "집",
                                    "isDefault": true
                                }
                                """))
                .andExpect(status().isCreated())                // 201
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.receiverName").value("체리"))
                .andExpect(jsonPath("$.isDefault").value(true));
    }

    @Test
    @DisplayName("POST /users/me/addresses - 필수 필드 누락 시 400")
    @WithMockUser
    void 배송지_추가_API_유효성검사_실패() throws Exception {
        // given
        // @NotBlank 필드(receiverName, phone, zipcode, address1)나 @NotNull(isDefault) 누락 시
        // @Valid에 의해 400 Bad Request 반환 → Service까지 가지 않음

        // when & then
        mockMvc.perform(post("/users/me/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "receiverName": "체리"
                                }
                                """))
                .andExpect(status().isBadRequest());  // 400
    }

    @Test
    @DisplayName("PATCH /users/me/addresses/{id} - 성공 시 200과 수정된 배송지 반환")
    @WithMockUser
    void 배송지_수정_API_성공() throws Exception {
        // given
        AddressUpdateResponse response = AddressUpdateResponse.builder()
                .id(1L)
                .label("회사")
                .isDefault(false)
                .build();

        // eq(1L) → PathVariable addressId가 정확히 1L인 경우에만 매핑
        given(addressService.updateAddress(any(), eq(1L), any())).willReturn(response);

        // when & then
        // PATCH는 변경할 필드만 포함 — label만 수정
        mockMvc.perform(patch("/users/me/addresses/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "label": "회사"
                                }
                                """))
                .andExpect(status().isOk()) // 200
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.label").value("회사"));
    }

    @Test
    @DisplayName("PATCH /users/me/addresses/{id} - 본인 배송지 아님 404")
    @WithMockUser
    void 배송지_수정_API_배송지없음_404() throws Exception {
        // given
        given(addressService.updateAddress(any(), eq(999L), any()))
                .willThrow(new BusinessException(ErrorCode.ADDRESS_NOT_FOUND));

        // when & then
        mockMvc.perform(patch("/users/me/addresses/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "label": "회사"
                                }
                                """))
                .andExpect(status().isNotFound()) // 404
                .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));
    }

    @Test
    @DisplayName("DELETE /users/me/addresses/{id} - 성공 시 204 반환")
    @WithMockUser
    void 배송지_삭제_API_성공() throws Exception {
        // given
        // deleteAddress는 void 반환이므로 willDoNothing() 사용
        willDoNothing().given(addressService).deleteAddress(any(), eq(1L));

        // when & then
        mockMvc.perform(delete("/users/me/addresses/1"))
                .andExpect(status().isNoContent());  // 204 (응답 바디 없음)
    }

    @Test
    @DisplayName("DELETE /users/me/addresses/{id} - 본인 배송지 아님 404")
    @WithMockUser
    void 배송지_삭제_API_배송지없음_404() throws Exception {
        // given
        // void 메서드 예외 설정은 willThrow(...).given(mock).method(...) 순서로 작성
        willThrow(new BusinessException(ErrorCode.ADDRESS_NOT_FOUND))
                .given(addressService).deleteAddress(any(), eq(999L));

        // when & then
        mockMvc.perform(delete("/users/me/addresses/999"))
                .andExpect(status().isNotFound())                         // 404
                .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));
    }
}
