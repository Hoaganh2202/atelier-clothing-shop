package com.shop.backend.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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
@Schema(description = "Yêu cầu cập nhật tồn kho biến thể theo cơ chế Compare-And-Swap")
public class InventoryStockUpdateRequest {

    @NotNull(message = "Số lượng tồn kho mới không được để trống")
    @Min(value = 0, message = "Số lượng tồn kho mới không được là số âm")
    @Max(value = 1000000, message = "Số lượng tồn kho mới không được vượt quá 1.000.000")
    @Schema(description = "Số lượng tồn kho mới cần cập nhật", example = "50")
    private Integer newStock;

    @NotNull(message = "Số lượng tồn kho kỳ vọng không được để trống")
    @Min(value = 0, message = "Số lượng tồn kho kỳ vọng không được là số âm")
    @Schema(description = "Số lượng tồn kho hiện tại được nhìn thấy trên màn hình để kiểm tra xung đột", example = "20")
    private Integer expectedStock;
}
