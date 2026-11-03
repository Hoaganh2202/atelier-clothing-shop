package com.shop.backend.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
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
public class ProductUpsertRequest {

    @NotBlank(message = "Tên sản phẩm không được để trống")
    @Size(max = 255, message = "Tên sản phẩm tối đa 255 ký tự")
    private String name;

    @Size(max = 255, message = "Slug tối đa 255 ký tự")
    @Pattern(
            regexp = "^$|^[a-z0-9]+(-[a-z0-9]+)*$",
            message = "Slug chỉ được chứa chữ thường, số và dấu gạch ngang, không bắt đầu hay kết thúc bằng gạch ngang"
    )
    private String slug;

    @NotNull(message = "Danh mục sản phẩm không được để trống")
    private Long categoryId;

    private String description;

    @NotNull(message = "Giá cơ bản không được để trống")
    @Min(value = 0, message = "Giá cơ bản phải lớn hơn hoặc bằng 0")
    @Max(value = 9999999999L, message = "Giá cơ bản không được vượt quá 9.999.999.999 VNĐ")
    private Long basePrice;

    @Builder.Default
    private Boolean isActive = true;

    @Valid
    @NotEmpty(message = "Sản phẩm phải có ít nhất 1 biến thể")
    private List<VariantUpsertRequest> variants;
}
