package com.shop.backend.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class VariantUpsertRequest {

    private Long id;

    @NotBlank(message = "Mã SKU không được để trống")
    @Size(max = 64, message = "Mã SKU tối đa 64 ký tự")
    private String sku;

    @Size(max = 20, message = "Kích cỡ tối đa 20 ký tự")
    @Builder.Default
    private String size = "N/A";

    @NotBlank(message = "Màu sắc không được để trống")
    @Size(max = 50, message = "Màu sắc tối đa 50 ký tự")
    private String color;

    @Schema(description = "Số lượng tồn kho ban đầu (chỉ bắt buộc và có tác dụng khi tạo biến thể mới; với biến thể đã có, giá trị này sẽ bị bỏ qua và không làm thay đổi tồn kho)")
    @Min(value = 0, message = "Tồn kho phải lớn hơn hoặc bằng 0")
    @Max(value = 1000000, message = "Tồn kho không được vượt quá 1.000.000")
    private Integer stock;

    @Min(value = 0, message = "Giá biến thể phải lớn hơn hoặc bằng 0")
    @Max(value = 9999999999L, message = "Giá biến thể không được vượt quá 9.999.999.999 VNĐ")
    private Long price;
}
