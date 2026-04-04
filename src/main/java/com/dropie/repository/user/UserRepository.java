package com.dropie.repository.user;

import com.dropie.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// JpaRepository<엔티티타입, PK타입> 을 상속하면
// save(), findById(), findAll() 등 기본 CRUD 메서드를 자동으로 제공해줌
public interface UserRepository extends JpaRepository<User, Long> {

    // Spring Data JPA는 메서드 이름을 분석해서 자동으로 쿼리를 만들어줌
    // findByEmail → "SELECT * FROM users WHERE email = ?" 쿼리가 자동 생성됨
    Optional<User> findByEmail(String email);

    // email이 이미 존재하는지 확인 (회원가입 중복 체크용)
    // existsBy~ → "SELECT COUNT(*) > 0 FROM users WHERE email = ?" 자동 생성
    boolean existsByEmail(String email);
}
