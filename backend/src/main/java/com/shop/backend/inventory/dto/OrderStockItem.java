package com.shop.backend.inventory.dto;

public record OrderStockItem(
        Long orderDetailId,
        Long variantId,
        Integer quantity
) {}
