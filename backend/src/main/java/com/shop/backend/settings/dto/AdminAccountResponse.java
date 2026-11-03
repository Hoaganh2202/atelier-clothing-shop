package com.shop.backend.settings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
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
@Schema(description = "Thông tin tài khoản quản trị đang đăng nhập")
public class AdminAccountResponse {

    @Schema(description = "Mã định danh người dùng", example = "1")
    private Long id;

    @Schema(description = "Họ và tên", example = "Nguyễn Văn Admin")
    private String name;

    @Schema(description = "Email tài khoản", example = "admin@shop.com")
    private String email;

    @Schema(description = "Số điện thoại", example = "0987654321")
    private String phone;

    @Schema(description = "Vai trò hệ thống", example = "admin")
    private String role;

    @Schema(description = "Lần đăng nhập gần nhất")
    private LocalDateTime lastLoginAt;

    @Schema(description = "Ngày tạo tài khoản")
    private LocalDateTime createdAt;
}
