package com.dropie.controller.tag;

import com.dropie.dto.response.tag.TagResponse;
import com.dropie.service.tag.TagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    // 전체 태그 목록 조회
    // GET /tags
    // 인증 필요 (온보딩 플로우 상 회원가입 이후 호출되므로 로그인 상태에서 요청됨)
    // Response: [{ "id": 1, "name": "#달콤한" }, ...]
    @GetMapping
    public ResponseEntity<List<TagResponse>> getTags() {
        log.debug("[GET /tags]");
        return ResponseEntity.ok(tagService.getTags());
    }

}
