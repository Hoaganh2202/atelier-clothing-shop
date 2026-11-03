package com.shop.backend.settings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Yêu cầu cập nhật thông tin tài khoản quản trị")
public class UpdateAdminAccountRequest {

    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 150, message = "Họ tên tối đa 150 ký tự")
    @Schema(description = "Họ và tên", example = "Nguyễn Văn Admin")
    private String name;

    @Pattern(
            regexp = "^$|^[0-9+()\\s.-]{8,20}$",
            message = "Số điện thoại không đúng định dạng"
    )
    @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
    @Schema(description = "Số điện thoại liên hệ", example = "0987654321")
    private String phone;
}
