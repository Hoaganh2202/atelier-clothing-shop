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
@Schema(description = "Dữ liệu tổng quan các chỉ số tài chính và đơn hàng trong kỳ báo cáo")
public class ReportSummaryResponse {

    @Schema(description = "Doanh thu hàng hóa gộp (Gross Merchandise Revenue - tổng tiền hàng theo snapshot giá)", example = "15000000")
    private Long grossRevenue;

    @Schema(description = "Doanh thu thuần thực tế (Net Revenue - tổng tiền thanh toán sau giảm giá và phí ship)", example = "14200000")
    private Long netRevenue;

    @Schema(description = "Tổng giá trị chiết khấu/giảm giá từ voucher", example = "1200000")
    private Long totalDiscount;

    @Schema(description = "Tổng phí vận chuyển của các đơn hoàn thành", example = "400000")
    private Long totalShippingFee;

    @Schema(description = "Tổng số đơn hàng hoàn thành thành công trong kỳ", example = "48")
    private Long completedOrderCount;

    @Schema(description = "Giá trị đơn hàng trung bình (AOV = netRevenue / completedOrderCount)", example = "295833")
    private Long averageOrderValue;
}
