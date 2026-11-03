package com.shop.backend.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardSummaryResponse {
    private Long lowStockCount;
    private Long newCustomersCount;
    private Double newCustomersChangeRate;
    private Long totalCustomersCount;
    private Long revenueTotal;
    private Double revenueChangeRate;
    private Long newOrdersCount;
    private Double newOrdersChangeRate;
}
