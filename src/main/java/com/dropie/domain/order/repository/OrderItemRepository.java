package com.dropie.domain.order.repository;

import com.dropie.domain.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

// 현재는 직접 사용하지 않고 Order cascade로 처리
// 추후 OrderItem 단건 조회가 필요할 때를 대비해 선언만 해둠
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
