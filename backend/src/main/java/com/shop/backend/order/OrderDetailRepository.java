package com.shop.backend.order;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderDetailRepository extends JpaRepository<OrderDetail, Long> {

    List<OrderDetail> findByOrderId(Long orderId);

    List<OrderDetail> findByOrderIdIn(Collection<Long> orderIds);

    long countByVariantId(Long variantId);

    List<OrderDetail> findByVariantId(Long variantId);
}
