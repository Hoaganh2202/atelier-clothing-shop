package com.shop.backend.inventory.dto;

import java.util.List;

public record OrderReturnReceiptResult(
        Long orderId,
        String receiptNumber,
        int totalReceivedQuantity,
        int restockedQuantity,
        int discardedQuantity,
        List<ReturnReceiptItemResult> itemResults
) {}
