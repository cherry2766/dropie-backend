package com.dropie.service.tag;


import com.dropie.dto.response.tag.TagResponse;
import com.dropie.repository.tag.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;

    @Transactional(readOnly = true)
    public List<TagResponse> getTags() {
        log.debug("[getTags] 전체 태그 조회");

        return tagRepository.findAll()
                .stream()
                .map(TagResponse::new)
                .toList();
    }
}
