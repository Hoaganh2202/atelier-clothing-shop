package com.shop.backend.voucher.dto;

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
public class VoucherStatusRequest {

    @NotNull(message = "Trạng thái bật/tắt không được để trống")
    private Boolean isActive;
}
