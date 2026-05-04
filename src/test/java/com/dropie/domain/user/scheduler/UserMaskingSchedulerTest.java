package com.dropie.domain.user.scheduler;

import com.dropie.domain.user.entity.Role;
import com.dropie.domain.user.entity.User;
import com.dropie.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UserMaskingSchedulerTest {

    @InjectMocks
    private UserMaskingScheduler scheduler;

    @Mock
    private UserRepository userRepository;

    // 테스트 헬퍼: User의 id는 @GeneratedValue라 빌더로 못 넣음 → 리플렉션으로 강제 주입
    private User userWithId(Long id, String email, String nickname) throws Exception {
        User user = User.builder()
                .email(email)
                .password("pw")
                .nickname(nickname)
                .role(Role.USER)
                .build();
        Field idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, id);
        user.withdraw(); // deletedAt 세팅 + 닉네임 즉시 마스킹
        return user;
    }

    @Test
    @DisplayName("마스킹 배치 - 30일 경과 유저의 이메일이 변조됨 (닉네임은 이미 탈퇴 시점에 마스킹됨)")
    void 마스킹_배치_정상_동작() throws Exception {
        User u1 = userWithId(1L, "a@email.com", "사과");
        User u2 = userWithId(2L, "b@email.com", "바나나");
        given(userRepository.findByDeletedAtBeforeAndEmailNotStartingWith(
                any(LocalDateTime.class), eq("withdrawn_")))
                .willReturn(List.of(u1, u2));

        scheduler.maskWithdrawnUsers();

        // 이메일은 스케줄러가 변조
        assertThat(u1.getEmail()).isEqualTo("withdrawn_1@masked.local");
        assertThat(u2.getEmail()).isEqualTo("withdrawn_2@masked.local");

        // 닉네임은 이미 withdraw()에서 변조된 상태
        assertThat(u1.getNickname()).isEqualTo("탈퇴회원_1");
        assertThat(u2.getNickname()).isEqualTo("탈퇴회원_2");
    }

    @Test
    @DisplayName("마스킹 배치 - 대상이 없으면 아무 작업도 하지 않음")
    void 마스킹_배치_대상없음() {
        given(userRepository.findByDeletedAtBeforeAndEmailNotStartingWith(
                any(LocalDateTime.class), eq("withdrawn_")))
                .willReturn(Collections.emptyList());

        scheduler.maskWithdrawnUsers();

        then(userRepository).should().findByDeletedAtBeforeAndEmailNotStartingWith(
                any(LocalDateTime.class), eq("withdrawn_"));
    }

    @Test
    @DisplayName("마스킹 배치 - threshold가 정확히 현재시각 - 30일")
    void 마스킹_배치_경계값_threshold_검증() {
        org.mockito.ArgumentCaptor<LocalDateTime> captor =
                org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        given(userRepository.findByDeletedAtBeforeAndEmailNotStartingWith(
                captor.capture(), eq("withdrawn_")))
                .willReturn(Collections.emptyList());

        LocalDateTime before = LocalDateTime.now().minusDays(30);
        scheduler.maskWithdrawnUsers();
        LocalDateTime after = LocalDateTime.now().minusDays(30);

        assertThat(captor.getValue()).isBetween(before, after);
    }
}