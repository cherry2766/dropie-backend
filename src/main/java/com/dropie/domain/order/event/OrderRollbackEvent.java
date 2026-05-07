package com.dropie.domain.order.event;

// 결제 실패 시 주문·재고 보상이 필요함을 알리는 도메인 이벤트
// → PaymentService.confirmPayment()의 catch 블록에서 publish됨
// → @TransactionalEventListener(AFTER_ROLLBACK)로 외부 트랜잭션 롤백 직후 별도 트랜잭션에서 처리
//
// 왜 이벤트로 분리?
// 같은 트랜잭션 안에서 dirty-checking으로 cancel + 재고 복원하면 throw e로 인한
// 트랜잭션 롤백에 변경이 함께 무효화됨. 이벤트로 분리하면 외부 트랜잭션이 완전히
// 롤백되어 비관적 락이 해제된 후 보상 작업이 새 트랜잭션에서 실행됨.
public record OrderRollbackEvent(Long orderId) {}
