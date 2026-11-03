package com.shop.backend.order.dto;

import java.math.BigDecimal;

public interface PeriodRevenueProjection {
    String getPeriod();
    BigDecimal getNetRevenue();
    BigDecimal getGrossRevenue();
    Long getCompletedOrderCount();
}
