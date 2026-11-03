package com.shop.backend.order.dto;

import lombok.Builder;

@Builder
public record CustomerOrderPreviewItemResponse(
        String productName,
        String variantSize,
        String variantColor,
        Integer quantity
) {}
