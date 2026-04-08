package com.dropie.service.preference;


import com.dropie.domain.preference.UserPreference;
import com.dropie.domain.tag.Tag;
import com.dropie.domain.user.User;
import com.dropie.dto.request.user.PreferenceRequest;
import com.dropie.exception.BusinessException;
import com.dropie.exception.ErrorCode;
import com.dropie.exception.custom.UserNotFoundException;
import com.dropie.repository.preference.UserPreferenceRepository;
import com.dropie.repository.tag.TagRepository;
import com.dropie.repository.user.UserRepository;
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
                .orElseThrow(UserNotFoundException::new);

        // 2. 요청된 tagId 목록으로 Tag 엔티티 조회
        List<Tag> tags = tagRepository.findAllByIdIn(request.getTagIds());

        // 3. 존재하지 않는 tagId가 포함된 경우 예외
        // 요청한 tagIds 수와 실제 조회된 Tag 수가 다르면 잘못된 tagId가 있는 것
        if (tags.size() != request.getTagIds().size()) {
            throw new BusinessException(ErrorCode.TAG_NOT_FOUND);
        }

        // 4. 기존 취향 태그 전체 삭제 (온보딩 재시도 시 덮어쓰기)
        userPreferenceRepository.deleteByUser(user);

        // 5. 새 UserPreference 생성 후 저장
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
