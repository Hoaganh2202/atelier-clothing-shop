package com.shop.backend.inventory.dto;

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
@Schema(description = "Thống kê tổng quan tình trạng kho hàng")
public class InventorySummaryResponse {

    @Schema(description = "Tổng số lượng biến thể sản phẩm", example = "120")
    private Long totalVariants;

    @Schema(description = "Tổng số lượng tồn kho cộng dồn của toàn bộ biến thể", example = "4500")
    private Long totalStock;

    @Schema(description = "Số lượng biến thể đã hết hàng (stock = 0)", example = "8")
    private Long outOfStockCount;

    @Schema(description = "Số lượng biến thể sắp hết hàng (0 < stock < threshold)", example = "15")
    private Long lowStockCount;

    @Schema(description = "Số lượng biến thể còn nhiều hàng (stock >= threshold)", example = "97")
    private Long inStockCount;
}
