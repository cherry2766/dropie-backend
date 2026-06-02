package com.dropie.domain.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 클라이언트가 POST /auth/signup 으로 보내는 JSON을 받는 객체
// {
//   "email": "test@test.com",
//   "password": "1234",
//   "nickname": "테스트"
// }
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class SignUpRequest {

    // @NotBlank : null, 빈 문자열(""), 공백(" ") 모두 거부
    // @Email    : 이메일 형식(@가 없는 등) 검증
    @NotBlank(message = "이메일을 입력해주세요.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String email;

    // @Size    : 최소 8자 ~ 최대 20자 제한
    // @Pattern : 정규식으로 영문 + 숫자 조합 필수 검증
    //   (?=.*[A-Za-z]) → 영문자가 최소 1개 이상 포함
    //   (?=.*\\d)       → 숫자가 최소 1개 이상 포함
    //   .+              → 나머지 문자는 뭐든 허용
    // → 소셜 로그인 사용자는 비밀번호가 없으므로, 소셜 로그인 추가 시
    //   이 검증을 provider 타입에 따라 조건부로 처리해야 함
    @NotBlank(message = "비밀번호를 입력해주세요.")
    @Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하여야 합니다.")
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
            message = "비밀번호는 영문과 숫자를 포함해야 합니다."
    )
    private String password;

    // @Size    : 최소 2자 ~ 최대 10자 제한
    // @Pattern : 한글, 영문, 숫자만 허용 (특수문자 금지)
    //   → 특수문자가 포함된 닉네임은 XSS 공격의 수단이 될 수 있어 차단
    @NotBlank(message = "닉네임을 입력해주세요.")
    @Size(min = 2, max = 10, message = "닉네임은 2자 이상 10자 이하여야 합니다.")
    @Pattern(
            regexp = "^[가-힣a-zA-Z0-9]+$",
            message = "닉네임에 특수문자를 사용할 수 없습니다."
    )
    private String nickname;
}
