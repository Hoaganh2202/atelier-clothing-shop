package com.shop.backend.einvoice.repository;

import com.shop.backend.einvoice.model.FulfillmentEventItem;
import com.shop.backend.einvoice.model.FulfillmentStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FulfillmentEventItemRepository extends JpaRepository<FulfillmentEventItem, Long> {

    List<FulfillmentEventItem> findByFulfillmentEventId(Long fulfillmentEventId);

    List<FulfillmentEventItem> findByOrderDetailId(Long orderDetailId);

    @Query("SELECT COALESCE(SUM(fei.deliveredQuantity), 0) " +
           "FROM FulfillmentEventItem fei " +
           "JOIN fei.fulfillmentEvent fe " +
           "WHERE fei.orderDetail.id = :orderDetailId " +
           "  AND fe.status = :status")
    int getDeliveredQuantityForOrderDetail(
            @Param("orderDetailId") Long orderDetailId,
            @Param("status") FulfillmentStatus status);
}
