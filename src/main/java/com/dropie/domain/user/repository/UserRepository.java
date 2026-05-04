package com.dropie.domain.user.repository;

import com.dropie.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// JpaRepository<엔티티타입, PK타입> 을 상속하면
// save(), findById(), findAll() 등 기본 CRUD 메서드를 자동으로 제공해줌
public interface UserRepository extends JpaRepository<User, Long> {

    // Spring Data JPA는 메서드 이름을 분석해서 자동으로 쿼리를 만들어줌
    // findByEmail → "SELECT * FROM users WHERE email = ?" 쿼리가 자동 생성됨
    Optional<User> findByEmail(String email);

    // 회원가입 2단계 체크에서 "최근 탈퇴 이메일" 검사용 + DataInitializer admin 시드 중복 체크
    boolean existsByEmail(String email);

    // 활성 유저(미탈퇴) 중에 동일 이메일이 있는지 확인
    boolean existsByEmailAndDeletedAtIsNull(String email);

    // 활성 유저(미탈퇴) 중에 동일 닉네임이 있는지 확인
    boolean existsByNicknameAndDeletedAtIsNull(String nickname);

    // 마스킹 배치 대상 조회
    // -> 탈퇴한 지 30일 이상 지났고, 아직 이메일이 마스킹되지 않은 유저
    @Query("""
            SELECT u 
            FROM User u 
            WHERE u.deletedAt < :threshold
            AND u.email NOT LIKE CONCAT(:prefix, '%') 
            """)
    List<User> findByDeletedAtBeforeAndEmailNotStartingWith(
            @Param("threshold") LocalDateTime threshold,
            @Param("prefix") String prefix
    );
}
