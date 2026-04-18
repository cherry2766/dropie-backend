package com.dropie.domain.preference.service;

import com.dropie.domain.preference.entity.UserPreference;
import com.dropie.domain.tag.entity.Tag;
import com.dropie.domain.user.entity.User;
import com.dropie.domain.preference.dto.request.PreferenceRequest;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import com.dropie.global.exception.custom.UserNotFoundException;
import com.dropie.domain.preference.repository.UserPreferenceRepository;
import com.dropie.domain.tag.repository.TagRepository;
import com.dropie.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreferenceService {

    private final UserRepository userRepository;
    private final TagRepository tagRepository;
    private final UserPreferenceRepository userPreferenceRepository;

    // 온보딩 default score 상수
    // 온보딩에서는 모든 선택 태그를 동일 가중치로 저장
    private static final int DEFAULT_SCORE = 5;

    @Transactional
    public void savePreferences(String email, PreferenceRequest request) {
        log.debug("[savePreferences] email: {}", email);

        // 1. 이메일로 유저 조회 → 없으면 예외
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("[savePreferences] 유저 없음 - email: {}", email);
                    return new UserNotFoundException();
                });

        // 2. tagIds가 없으면 스킵 (온보딩은 선택 사항)
        if (request.getTagIds() == null || request.getTagIds().isEmpty()) {

            log.debug("[savePreferences] tagIds 없음 - 스킵");
            return;
        }

        // 3. 요청된 tagId 목록으로 Tag 엔티티 조회
        List<Tag> tags = tagRepository.findAllByIdIn(request.getTagIds());

        // 4. 존재하지 않는 tagId가 포함된 경우 예외
        // 요청한 tagIds 수와 실제 조회된 Tag 수가 다르면 잘못된 tagId가 있는 것
        if (tags.size() != request.getTagIds().size()) {
            throw new BusinessException(ErrorCode.TAG_NOT_FOUND);
        }

        // 5. 기존 취향 태그 전체 삭제 (온보딩 재시도 시 덮어쓰기)
        userPreferenceRepository.deleteByUser(user);

        // 6. 새 UserPreference 생성 후 저장
        List<UserPreference> preferences = tags.stream()
                .map(tag -> UserPreference.builder()
                        .user(user)
                        .tag(tag)
                        .score(DEFAULT_SCORE)
                        .build())
                .toList();

        userPreferenceRepository.saveAll(preferences);

        log.info("[savePreferences] 저장 완료 - userId: {}, tagCount: {}", user.getId(), tags.size());
    }



}
