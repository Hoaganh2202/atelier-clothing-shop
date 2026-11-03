package com.shop.backend.inventory.dto;

public record OrderReturnReceiptItem(
        Long orderDetailId,
        Integer quantity,
        ReturnItemCondition condition,
        String reason
) {}
