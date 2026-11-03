package com.shop.backend.inventory;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    List<InventoryTransaction> findByOrderIdAndMovementType(Long orderId, InventoryMovementType movementType);

    boolean existsByOrderIdAndMovementType(Long orderId, InventoryMovementType movementType);

    Optional<InventoryTransaction> findByIdempotencyKey(String idempotencyKey);

    List<InventoryTransaction> findByIdempotencyKeyStartingWith(String prefix);

    @Query("SELECT COALESCE(SUM(tx.quantity), 0) FROM InventoryTransaction tx " +
           "WHERE tx.orderDetail.id = :orderDetailId " +
           "AND tx.movementType IN (com.shop.backend.inventory.InventoryMovementType.RETURN_RESTOCK, com.shop.backend.inventory.InventoryMovementType.RETURN_DISCARD)")
    int getPreviouslyReceivedQuantity(@Param("orderDetailId") Long orderDetailId);
}
