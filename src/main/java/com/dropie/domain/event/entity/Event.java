package com.dropie.domain.event.entity;

import com.dropie.global.common.BaseEntity;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import com.dropie.domain.product.entity.Product;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Event extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String brandName;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String thumbnailImageUrl;

    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    // cascade = CascadeType.ALL: 이벤트 삭제 시 하위 상품도 JPA가 함께 삭제
    // orphanRemoval = true: 이벤트에서 분리된 상품(고아 객체)도 자동 삭제
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Product> products = new ArrayList<>();

    // PATCH 부분 업데이트 — null이면 기존 값 유지
    // brandName은 수정 불가로 설계
    public void update(String description, String thumbnailImageUrl, String imageUrl, LocalDateTime startAt, LocalDateTime endAt) {
        if(description != null) this.description = description;
        if(thumbnailImageUrl != null) this.thumbnailImageUrl = thumbnailImageUrl;
        if(imageUrl != null) this.imageUrl = imageUrl;
        if(startAt != null) this.startAt = startAt;
        if(endAt != null) this.endAt = endAt;
    }

    // 상태 전환 — 허용 불가면 INVALID_STATUS_TRANSITION 예외
    // canTransitionTo()로 전환 가능 여부를 EventStatus 자체에서 판단
    public void changeStatus(EventStatus newStatus) {
        if(!this.status.canTransitionTo(newStatus)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        this.status = newStatus;
    }
}
