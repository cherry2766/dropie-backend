package com.dropie.domain.preference.service;

import com.dropie.domain.preference.entity.UserPreference;
import com.dropie.domain.recommendation.service.TasteTagService;
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
    private final TasteTagService tasteTagService;

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

        // 5. 회원가입 태그는 한 번만 등록 가능 (이후 수정 불가 정책)
        //    프론트는 회원가입 흐름에서만 호출하지만,
        //    API 직접 호출에 대비한 백엔드 안전망
        if (userPreferenceRepository.existsByUser(user)) {
            throw new BusinessException(ErrorCode.PREFERENCE_ALREADY_REGISTERED);
        }

        // 6. 새 UserPreference 생성 후 저장
        List<UserPreference> preferences = tags.stream()
                .map(tag -> UserPreference.builder()
                        .user(user)
                        .tag(tag)
                        .build())
                .toList();

        userPreferenceRepository.saveAll(preferences);

        // 7. 회원가입 태그를 ZSET 시드로 흘려보냄
        tasteTagService.addSeedScores(user.getId(), request.getTagIds());

        log.info("[savePreferences] 저장 완료 - userId: {}, tagCount: {}", user.getId(), tags.size());
    }



}
