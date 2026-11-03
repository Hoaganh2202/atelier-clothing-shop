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
@Schema(description = "Điểm dữ liệu xu hướng doanh thu theo mốc thời gian")
public class RevenueReportPointResponse {

    @Schema(description = "Nhãn mốc thời gian (YYYY-MM-DD cho ngày, YYYY-Www cho tuần, YYYY-MM cho tháng)", example = "2026-10-30")
    private String period;

    @Schema(description = "Doanh thu thuần thực tế (Net Revenue) trong mốc thời gian", example = "2500000")
    private Long netRevenue;

    @Schema(description = "Doanh thu hàng hóa gộp (Gross Merchandise Revenue) trong mốc thời gian", example = "2700000")
    private Long grossRevenue;

    @Schema(description = "Số lượng đơn hàng hoàn thành trong mốc thời gian", example = "8")
    private Long completedOrderCount;
}
