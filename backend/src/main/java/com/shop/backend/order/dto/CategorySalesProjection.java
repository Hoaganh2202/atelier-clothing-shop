package com.shop.backend.order.dto;

import java.math.BigDecimal;

public interface CategorySalesProjection {
    Long getCategoryId();
    String getCategoryName();
    Long getTotalQuantitySold();
    BigDecimal getTotalRevenue();
}
