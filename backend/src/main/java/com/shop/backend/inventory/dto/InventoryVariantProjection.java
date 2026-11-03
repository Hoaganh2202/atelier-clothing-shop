package com.shop.backend.inventory.dto;

import java.math.BigDecimal;
import lombok.Getter;

@Getter
public class InventoryVariantProjection {
    private final Long id;
    private final Long productId;
    private final String productName;
    private final Boolean productIsActive;
    private final String primaryImageUrl;
    private final String categoryName;
    private final String sku;
    private final String size;
    private final String color;
    private final Integer stock;
    private final BigDecimal price;

    public InventoryVariantProjection(
            Long id,
            Long productId,
            String productName,
            Boolean productIsActive,
            String primaryImageUrl,
            String categoryName,
            String sku,
            String size,
            String color,
            Integer stock,
            BigDecimal price
    ) {
        this.id = id;
        this.productId = productId;
        this.productName = productName;
        this.productIsActive = productIsActive;
        this.primaryImageUrl = primaryImageUrl;
        this.categoryName = categoryName;
        this.sku = sku;
        this.size = size;
        this.color = color;
        this.stock = stock;
        this.price = price;
    }
}
