package com.shop.backend.inventory.dto;

import java.util.List;

public record OrderStockRestoreResult(
        int restoredItemsCount,
        int skippedItemsCount,
        List<Long> restoredVariantIds
) {}
