package com.shop.backend.report.dto;

import com.shop.backend.order.OrderStatus;
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
@Schema(description = "Cơ cấu và tỷ lệ phân bố các trạng thái đơn hàng được tạo trong kỳ")
public class OrderStatusDistributionResponse {

    @Schema(description = "Mã trạng thái đơn hàng", example = "COMPLETED")
    private OrderStatus status;

    @Schema(description = "Tên hiển thị tiếng Việt của trạng thái", example = "Đã hoàn thành")
    private String statusLabel;

    @Schema(description = "Số lượng đơn hàng thuộc trạng thái này", example = "45")
    private Long orderCount;

    @Schema(description = "Tỷ lệ phần trăm trên tổng số đơn được tạo trong kỳ (%)", example = "75.0")
    private Double percentage;
}
