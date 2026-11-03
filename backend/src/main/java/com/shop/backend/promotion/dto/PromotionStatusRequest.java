package com.shop.backend.promotion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
public class PromotionStatusRequest {

    @NotNull(message = "Trạng thái bật/tắt không được để trống")
    @Schema(description = "Trạng thái kích hoạt mới (true = bật, false = tắt)")
    private Boolean isActive;
}
