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
public class LowStockVariantResponse {
    private Long id;
    private Long productId;
    private String productName;
    private String sku;
    private String size;
    private String color;
    private Integer stock;
}
