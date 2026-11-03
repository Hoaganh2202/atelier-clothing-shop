package com.shop.backend.order.dto;

import java.math.BigDecimal;

public interface ReportSummaryProjection {
    BigDecimal getGrossRevenue();
    BigDecimal getNetRevenue();
    BigDecimal getTotalDiscount();
    BigDecimal getTotalShippingFee();
    Long getCompletedOrderCount();
}
