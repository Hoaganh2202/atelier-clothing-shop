package com.shop.backend.inventory.dto;

public record ReturnReceiptItemResult(
        Long orderDetailId,
        Long variantId,
        int quantity,
        ReturnItemCondition condition,
        int stockBefore,
        int stockAfter,
        String idempotencyKey
) {}
