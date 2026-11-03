package com.shop.backend.product.dto;

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
public class ProductVariantPublicResponse {
    private Long id;
    private String size;
    private String color;
    private Long effectivePrice;
    private Integer stock;
}
