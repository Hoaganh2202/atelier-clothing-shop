package com.shop.backend.inventory;

import com.shop.backend.common.dto.PageResponse;
import com.shop.backend.inventory.dto.InventoryStockUpdateRequest;
import com.shop.backend.inventory.dto.InventorySummaryResponse;
import com.shop.backend.inventory.dto.InventoryVariantResponse;

public interface InventoryAdminService {

    PageResponse<InventoryVariantResponse> getVariants(
            String keyword,
            Long categoryId,
            String size,
            String color,
            String stockStatus,
            Integer threshold,
            Boolean productActive,
            int page,
            int pageSize,
            String sort
    );

    InventorySummaryResponse getSummary(Integer threshold);

    InventoryVariantResponse updateStock(Long id, InventoryStockUpdateRequest request);
}
