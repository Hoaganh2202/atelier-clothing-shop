package com.shop.backend.product.dto;

import java.util.List;
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
public class ProductPublicDetailResponse {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private ProductCategoryResponse category;
    private List<ProductImageResponse> images;
    private List<ProductVariantPublicResponse> variants;
    private Long minPrice;
    private Long maxPrice;
    private Boolean inStock;
    private List<ProductCardResponse> relatedProducts;
}
