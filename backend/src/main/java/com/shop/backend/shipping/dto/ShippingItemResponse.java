package com.shop.backend.shipping.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingItemResponse {

    private Long id;
    private Long variantId;
    private String productName;
    private String variantSize;
    private String variantColor;
    private int quantity;
    private Long unitPrice;
    private Long totalPrice;
}
