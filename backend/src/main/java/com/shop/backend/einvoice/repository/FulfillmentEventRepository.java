package com.shop.backend.einvoice.repository;

import com.shop.backend.einvoice.model.FulfillmentEvent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FulfillmentEventRepository extends JpaRepository<FulfillmentEvent, Long> {

    Optional<FulfillmentEvent> findByFulfillmentCode(String fulfillmentCode);

    List<FulfillmentEvent> findByOrderId(Long orderId);

    boolean existsByFulfillmentCode(String fulfillmentCode);
}
