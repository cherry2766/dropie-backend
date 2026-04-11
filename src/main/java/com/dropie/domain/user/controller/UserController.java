package com.dropie.domain.user.controller;

import com.dropie.domain.user.dto.response.UserResponse;
import com.dropie.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // 내 정보 조회 API
    // GET /users/me
    // Header: Authorization: Bearer {토큰}
    // Response: { "id": 1, "email": "...", "nickname": "...", "role": "USER" }
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(@AuthenticationPrincipal String email) {
        // @AuthenticationPrincipal : SecurityContext에 저장된 principal을 꺼내줌
        // JwtAuthenticationFilter에서 new UsernamePasswordAuthenticationToken(email, ...)
        // 으로 email을 principal에 넣었기 때문에 String으로 바로 받을 수 있음
        log.debug("[GET/users/me] email: {}", email);

        return ResponseEntity.ok(userService.getMe(email));
    }
}
