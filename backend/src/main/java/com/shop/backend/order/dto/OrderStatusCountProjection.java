package com.shop.backend.order.dto;

public interface OrderStatusCountProjection {
    String getStatus();
    Long getOrderCount();
}
