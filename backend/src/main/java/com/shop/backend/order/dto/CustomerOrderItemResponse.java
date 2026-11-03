package com.shop.backend.order.dto;

import lombok.Builder;

@Builder
public record CustomerOrderItemResponse(
        Long orderDetailId,
        String productName,
        String variantSize,
        String variantColor,
        Integer quantity,
        Long unitPrice,
        Long discountAmount,
        Long lineTotal
) {}
