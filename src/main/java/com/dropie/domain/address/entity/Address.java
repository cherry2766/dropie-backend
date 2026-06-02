package com.dropie.domain.address.entity;

import com.dropie.domain.user.entity.User;
import com.dropie.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "addresses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Address extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String receiverName;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String zipcode;

    @Column(nullable = false)
    private String address1;

    private String address2;

    private String label;

    @Column(nullable = false)
    private boolean isDefault;

    // PATCH 부분 업데이트 — null이면 기존 값 유지
    public void update(String receiverName, String phone, String zipcode,
                       String address1, String address2, String label, Boolean isDefault) {
        if (receiverName != null) this.receiverName = receiverName;
        if (phone != null) this.phone = phone;
        if (zipcode != null) this.zipcode = zipcode;
        if (address1 != null) this.address1 = address1;
        if (address2 != null) this.address2 = address2;
        if (label != null) this.label = label;
        if (isDefault != null) this.isDefault = isDefault;
    }

    // 기본 배송지 해제 — 다른 배송지를 기본으로 설정할 때 기존 것들을 false로 초기화
    public void clearDefault() {
        this.isDefault = false;
    }

    // 기본 배송지로 지정 — 삭제 후 자동 지정 시 사용
    public void setDefaultAddress() {
        this.isDefault = true;
    }
}
