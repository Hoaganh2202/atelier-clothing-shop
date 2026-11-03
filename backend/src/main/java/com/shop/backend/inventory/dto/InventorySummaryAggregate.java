package com.shop.backend.inventory.dto;

import lombok.Getter;

@Getter
public class InventorySummaryAggregate {
    private final Long totalVariants;
    private final Long totalStock;
    private final Long outOfStockCount;
    private final Long lowStockCount;
    private final Long inStockCount;

    public InventorySummaryAggregate(
            Long totalVariants,
            Long totalStock,
            Long outOfStockCount,
            Long lowStockCount,
            Long inStockCount
    ) {
        this.totalVariants = totalVariants != null ? totalVariants : 0L;
        this.totalStock = totalStock != null ? totalStock : 0L;
        this.outOfStockCount = outOfStockCount != null ? outOfStockCount : 0L;
        this.lowStockCount = lowStockCount != null ? lowStockCount : 0L;
        this.inStockCount = inStockCount != null ? inStockCount : 0L;
    }
}
