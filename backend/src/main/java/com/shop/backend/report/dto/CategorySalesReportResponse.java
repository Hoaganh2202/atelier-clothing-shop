package com.shop.backend.report.dto;

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
@Schema(description = "Doanh số và sản lượng bán theo danh mục trong kỳ báo cáo")
public class CategorySalesReportResponse {

    @Schema(description = "ID của danh mục", example = "5")
    private Long categoryId;

    @Schema(description = "Tên danh mục hàng hóa", example = "Áo Sơ Mi Nam")
    private String categoryName;

    @Schema(description = "Tổng số lượng sản phẩm thuộc danh mục đã bán", example = "210")
    private Long totalQuantitySold;

    @Schema(description = "Tổng doanh thu danh mục mang lại theo snapshot", example = "63000000")
    private Long totalRevenue;

    @Schema(description = "Tỷ trọng doanh thu của danh mục trên tổng doanh thu các danh mục (%)", example = "35.5")
    private Double percentage;
}
