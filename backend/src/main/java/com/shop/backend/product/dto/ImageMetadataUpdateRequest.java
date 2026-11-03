package com.shop.backend.product.dto;

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
public class ImageMetadataUpdateRequest {

    @Size(max = 255, message = "Văn bản thay thế tối đa 255 ký tự")
    private String alt;

    private Boolean isPrimary;

    private Integer sortOrder;

    @Size(max = 50, message = "Mã màu tối đa 50 ký tự")
    private String color;
}
