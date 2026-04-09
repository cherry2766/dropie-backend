package com.dropie.controller.preference;

import com.dropie.dto.request.preference.PreferenceRequest;
import com.dropie.service.preference.PreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/users/me/preferences")
@RequiredArgsConstructor
public class PreferenceController {

    private final PreferenceService preferenceService;

    // 취향 태그 등록 (온보딩)
    // POST /users/me/preferences
    // Header: Authorization: Bearer {토큰}
    // Request: { "tagIds": [1, 2, 3] }
    // Response: 204 No Content (body 없음)
    @PostMapping
    public ResponseEntity<Void> savePreferences(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody PreferenceRequest request) {

        log.debug("[POST /users/me/preferences] email: {}", email);

        preferenceService.savePreferences(email, request);

        return ResponseEntity.noContent().build();

    }
}
