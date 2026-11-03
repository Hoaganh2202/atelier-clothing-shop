package com.shop.backend.promotion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
public class PromotionProductItemResponse {

    @Schema(description = "ID sản phẩm")
    private Long id;

    @Schema(description = "Tên sản phẩm")
    private String name;

    @Schema(description = "Đường dẫn định danh (slug)")
    private String slug;

    @Schema(description = "Giá gốc dạng số nguyên VND")
    private Long basePrice;

    @Schema(description = "Trạng thái hiển thị sản phẩm")
    private Boolean isActive;

    @Schema(description = "URL ảnh chính của sản phẩm", nullable = true)
    private String primaryImageUrl;
}
