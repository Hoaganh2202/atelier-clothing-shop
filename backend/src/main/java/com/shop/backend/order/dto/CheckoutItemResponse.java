package com.shop.backend.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutItemResponse {

    private Long orderDetailId;
    private Long variantId;
    private String productName;
    private String variantSize;
    private String variantColor;
    private Integer quantity;
    private Long unitPrice;
    private Long discountAmount;
    private Long totalPrice;
}
