package com.dropie.domain.tag.service;

import com.dropie.domain.tag.dto.response.TagResponse;
import com.dropie.domain.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TagService {

    private final TagRepository tagRepository;

    public List<TagResponse> getOnboardingTags() {
        return tagRepository.findAllByOnboardingExposedTrue().stream()
                .map(TagResponse::from)
                .toList();
    }

    public List<TagResponse> searchForAdmin(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return tagRepository.findTop20ByNameContaining(keyword.trim()).stream()
                .map(TagResponse::from)
                .toList();
    }
}
