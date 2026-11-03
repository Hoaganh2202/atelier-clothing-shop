package com.shop.backend.product.dto;

import java.time.LocalDateTime;
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
public class ProductDetailResponse {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private Long categoryId;
    private String categoryName;
    private Long basePrice;
    private Boolean isActive;
    private String dataSource;
    private String sourceProductCode;
    private LocalDateTime createdAt;
    private List<ProductVariantResponse> variants;
    private List<ProductImageResponse> images;
}
