package com.shop.backend.inventory;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderReturnReceiptRepository extends JpaRepository<OrderReturnReceipt, Long> {

    Optional<OrderReturnReceipt> findByOrderIdAndReceiptNumber(Long orderId, String receiptNumber);
}
