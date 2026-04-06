package com.dropie.repository.address;

import com.dropie.domain.address.Address;
import com.dropie.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// JpaRepository<엔티티, PK타입> — save/findById/delete 등 기본 메서드 자동 제공
public interface AddressRepository extends JpaRepository<Address, Long> {

    // 특정 유저의 배송지 목록 전체 조회
    List<Address> findAllByUser(User user);

    // 특정 유저의 특정 배송지 조회
    // findById만 쓰면 다른 유저의 배송지도 접근 가능해지므로 반드시 user 조건 포함
    Optional<Address> findByIdAndUser(Long id, User user);

    // 기본 배송지 해제용, 기본 배송지는 항상 1개
    Optional<Address> findByUserAndIsDefaultTrue(User user);
}
