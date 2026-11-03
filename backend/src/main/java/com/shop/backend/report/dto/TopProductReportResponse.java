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
@Schema(description = "Thông tin sản phẩm bán chạy trong kỳ báo cáo")
public class TopProductReportResponse {

    @Schema(description = "ID của sản phẩm trong hệ thống", example = "101")
    private Long productId;

    @Schema(description = "Tên sản phẩm được lưu snapshot tại thời điểm mua hàng", example = "Áo Sơ Mi Oxford Slim-Fit")
    private String productName;

    @Schema(description = "Tổng số lượng đã bán của các đơn hoàn thành", example = "125")
    private Long totalQuantitySold;

    @Schema(description = "Tổng doanh thu sản phẩm mang lại theo snapshot giá mua", example = "37500000")
    private Long totalRevenue;
}
