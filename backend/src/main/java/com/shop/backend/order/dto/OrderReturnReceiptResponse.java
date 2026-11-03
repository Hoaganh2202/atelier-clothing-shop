package com.shop.backend.order.dto;

import com.shop.backend.inventory.dto.OrderReturnReceiptResult;
import java.util.List;

public record OrderReturnReceiptResponse(
        Long orderId,
        String receiptNumber,
        int totalReceivedQuantity,
        int restockedQuantity,
        int discardedQuantity,
        List<OrderReturnReceiptItemResponse> items
) {
    public static OrderReturnReceiptResponse from(OrderReturnReceiptResult result) {
        List<OrderReturnReceiptItemResponse> mappedItems = result.itemResults() == null
                ? List.of()
                : result.itemResults().stream()
                        .map(OrderReturnReceiptItemResponse::from)
                        .toList();

        return new OrderReturnReceiptResponse(
                result.orderId(),
                result.receiptNumber(),
                result.totalReceivedQuantity(),
                result.restockedQuantity(),
                result.discardedQuantity(),
                mappedItems
        );
    }
}
