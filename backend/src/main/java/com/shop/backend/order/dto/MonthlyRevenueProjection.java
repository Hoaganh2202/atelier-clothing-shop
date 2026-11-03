package com.shop.backend.order.dto;

import java.math.BigDecimal;

public interface MonthlyRevenueProjection {
    String getTrendMonth();
    BigDecimal getTotalRevenue();
    Long getTotalOrders();
}
