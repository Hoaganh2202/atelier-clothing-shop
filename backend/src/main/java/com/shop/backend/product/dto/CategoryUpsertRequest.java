package com.shop.backend.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
public class CategoryUpsertRequest {

    @NotBlank(message = "Tên danh mục không được để trống")
    @Size(max = 150, message = "Tên danh mục tối đa 150 ký tự")
    private String name;

    @Size(max = 180, message = "Slug tối đa 180 ký tự")
    @Pattern(
        regexp = "^$|^[a-z0-9]+(-[a-z0-9]+)*$",
        message = "Slug chỉ được chứa chữ thường không dấu, số và dấu gạch ngang, không bắt đầu hay kết thúc bằng gạch ngang"
    )
    private String slug;

    private Long parentId;

    @Builder.Default
    private Boolean isActive = true;
}
