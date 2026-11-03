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
@Schema(description = "Thông tin cấu hình cửa hàng và vận hành")
public class StoreSettingsResponse {

    @Schema(description = "Tên hiển thị của cửa hàng", example = "Closet Shop")
    private String storeName;

    @Schema(description = "Tên pháp nhân công ty", example = "Công ty TNHH Thời trang Closet")
    private String legalName;

    @Schema(description = "Mã số thuế doanh nghiệp", example = "0101234567")
    private String taxCode;

    @Schema(description = "Địa chỉ trụ sở / cửa hàng", example = "123 Đường Cầu Giấy, Hà Nội")
    private String address;

    @Schema(description = "Số điện thoại liên hệ", example = "0987654321")
    private String phone;

    @Schema(description = "Email liên hệ chính", example = "contact@closetshop.com")
    private String email;

    @Schema(description = "Đường dẫn ảnh logo", example = "/uploads/products/logo.png")
    private String logoUrl;

    @Schema(description = "Ngưỡng cảnh báo tồn kho thấp", example = "10")
    private Integer lowStockThreshold;

    @Schema(description = "Phí vận chuyển mặc định (VND)", example = "30000")
    private Long defaultShippingFee;

    @Schema(description = "Ngưỡng đơn hàng được miễn phí vận chuyển (VND)", example = "500000")
    private Long freeShippingThreshold;

    @Schema(description = "Thời điểm cập nhật gần nhất")
    private LocalDateTime updatedAt;

    @Schema(description = "Tài khoản thực hiện cập nhật gần nhất", example = "admin@shop.com")
    private String updatedBy;
}
