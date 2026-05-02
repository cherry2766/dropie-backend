package com.dropie.domain.tag.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "tags",
        uniqueConstraints = @UniqueConstraint(name = "uk_tag_name", columnNames = "name")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String name;

    // 회원가입 화면에 노출할지 여부
    //  - true:  DataInitializer로 만든 큐레이션 10개 (사용자 회원가입 선택지)
    //  - false: 어드민이 상품 등록 중 자동 생성한 세부 태그 (회원가입엔 안 보임)
    @Column(nullable = false)
    @Builder.Default
    private boolean onboardingExposed = false;
}
