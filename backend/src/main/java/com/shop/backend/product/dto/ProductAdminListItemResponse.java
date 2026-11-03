package com.shop.backend.product.dto;

import java.time.LocalDateTime;
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
public class ProductAdminListItemResponse {
    private Long id;
    private String name;
    private String slug;
    private Long categoryId;
    private String categoryName;
    private Long basePrice;
    private Long minPrice;
    private Long maxPrice;
    private Integer totalStock;
    private Boolean isActive;
    private String primaryImageUrl;
    private LocalDateTime createdAt;
}
