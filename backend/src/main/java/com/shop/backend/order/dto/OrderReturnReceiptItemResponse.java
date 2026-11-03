package com.shop.backend.order.dto;

import com.shop.backend.inventory.dto.ReturnItemCondition;
import com.shop.backend.inventory.dto.ReturnReceiptItemResult;

public record OrderReturnReceiptItemResponse(
        Long orderDetailId,
        Long variantId,
        int quantity,
        ReturnItemCondition condition,
        int stockBefore,
        int stockAfter,
        String idempotencyKey
) {
    public static OrderReturnReceiptItemResponse from(ReturnReceiptItemResult item) {
        return new OrderReturnReceiptItemResponse(
                item.orderDetailId(),
                item.variantId(),
                item.quantity(),
                item.condition(),
                item.stockBefore(),
                item.stockAfter(),
                item.idempotencyKey()
        );
    }
}
