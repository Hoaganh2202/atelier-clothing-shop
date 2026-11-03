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
public class ProductCardResponse {
    private Long id;
    private String name;
    private String slug;
    private String thumbnailUrl;
    private Long minPrice;
    private Long maxPrice;
    private Boolean inStock;
    private String categoryName;
}
