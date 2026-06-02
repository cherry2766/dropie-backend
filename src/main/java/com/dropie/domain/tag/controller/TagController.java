package com.dropie.domain.tag.controller;

import com.dropie.domain.tag.dto.response.TagResponse;
import com.dropie.domain.tag.service.TagService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "태그", description = "회원가입 노출 태그 목록 + 어드민 자동완성 검색")
@RestController
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    // 회원가입 화면 — onboardingExposed=true 만
    @GetMapping("/tags")
    public ResponseEntity<List<TagResponse>> getOnboardingTags() {
        return ResponseEntity.ok(tagService.getOnboardingTags());
    }

    // 어드민 자동완성 — 상품 등록 화면에서 태그 입력 시 부분 일치 검색
    //   GET /admin/tags?keyword=초    → "초콜릿", "초코칩" 등 노출
    @GetMapping("/admin/tags")
    public ResponseEntity<List<TagResponse>> searchAdminTags(
            @RequestParam(required = false, defaultValue = "") String keyword
    ) {
        return ResponseEntity.ok(tagService.searchForAdmin(keyword));
    }
}
