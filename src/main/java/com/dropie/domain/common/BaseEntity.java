package com.dropie.domain.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

// 공통 엔티티 (생성일 / 수정일 자동 관리)
@MappedSuperclass // 테이블로 생성되지 않고, 상속받은 엔티티에 필드만 포함됨
@EntityListeners(AuditingEntityListener.class) // Auditing 기능 활성화
public abstract class BaseEntity {

    @CreatedDate // 엔티티 생성 시 자동으로 시간 저장
    @Column(updatable = false) // 생성 시간은 수정 불가
    private LocalDateTime createdAt;

    @LastModifiedDate // 엔티티 수정 시 자동으로 시간 갱신
    private LocalDateTime updatedAt;
}
