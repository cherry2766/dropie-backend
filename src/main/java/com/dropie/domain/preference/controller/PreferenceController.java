package com.dropie.domain.preference.controller;

import com.dropie.domain.preference.dto.request.PreferenceRequest;
import com.dropie.domain.preference.service.PreferenceService;
import com.dropie.global.security.CustomUserDetails;
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
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PreferenceRequest request) {

        log.debug("[POST /users/me/preferences] email: {}", userDetails.getUsername());

        preferenceService.savePreferences(userDetails.getUsername(), request);

        return ResponseEntity.noContent().build();

    }
}
