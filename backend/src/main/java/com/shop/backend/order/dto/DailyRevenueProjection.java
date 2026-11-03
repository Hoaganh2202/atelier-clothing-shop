package com.shop.backend.order.dto;

import java.math.BigDecimal;
import java.sql.Date;

public interface DailyRevenueProjection {
    Date getTrendDate();
    BigDecimal getTotalRevenue();
    Long getTotalOrders();
}
