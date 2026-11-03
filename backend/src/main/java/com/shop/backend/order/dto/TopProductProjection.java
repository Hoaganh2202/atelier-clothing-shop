package com.shop.backend.order.dto;

import java.math.BigDecimal;

public interface TopProductProjection {
    Long getProductId();
    String getProductName();
    Long getTotalQuantitySold();
    BigDecimal getTotalRevenue();
}
