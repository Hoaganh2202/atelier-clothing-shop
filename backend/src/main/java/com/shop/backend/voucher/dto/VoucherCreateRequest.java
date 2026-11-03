package com.shop.backend.voucher.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class VoucherCreateRequest extends VoucherUpdateRequest {

    @NotBlank(message = "Mã voucher không được để trống")
    @Size(max = 50, message = "Mã voucher tối đa 50 ký tự")
    @Schema(description = "Được chuẩn hóa in hoa; chỉ gồm A-Z, 0-9, gạch ngang, gạch dưới, dài 3-50 ký tự; không đổi được sau khi tạo")
    private String code;
}
