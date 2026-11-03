package com.shop.backend.settings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
@Schema(description = "Yêu cầu cập nhật cấu hình cửa hàng")
public class StoreSettingsRequest {

    @NotBlank(message = "Tên cửa hàng không được để trống")
    @Size(max = 150, message = "Tên cửa hàng tối đa 150 ký tự")
    @Schema(description = "Tên hiển thị của cửa hàng", example = "Closet Shop")
    private String storeName;

    @Size(max = 255, message = "Tên pháp nhân tối đa 255 ký tự")
    @Schema(description = "Tên pháp nhân công ty", example = "Công ty TNHH Thời trang Closet")
    private String legalName;

    @Pattern(
            regexp = "^$|^(\\d{10}|\\d{10}-\\d{3})$",
            message = "Mã số thuế phải gồm 10 chữ số hoặc 13 chữ số (dạng 1234567890 hoặc 1234567890-001)"
    )
    @Size(max = 30, message = "Mã số thuế tối đa 30 ký tự")
    @Schema(description = "Mã số thuế (cho phép để trống)", example = "0101234567")
    private String taxCode;

    @Size(max = 300, message = "Địa chỉ tối đa 300 ký tự")
    @Schema(description = "Địa chỉ trụ sở / cửa hàng", example = "123 Đường Cầu Giấy, Hà Nội")
    private String address;

    @Pattern(
            regexp = "^$|^[0-9+()\\s.-]{8,20}$",
            message = "Số điện thoại không đúng định dạng"
    )
    @Size(max = 30, message = "Số điện thoại tối đa 30 ký tự")
    @Schema(description = "Số điện thoại liên hệ", example = "0987654321")
    private String phone;

    @Email(message = "Email liên hệ không hợp lệ")
    @Size(max = 150, message = "Email tối đa 150 ký tự")
    @Schema(description = "Email liên hệ chính", example = "contact@closetshop.com")
    private String email;

    @Size(max = 500, message = "Đường dẫn logo tối đa 500 ký tự")
    @Schema(description = "Đường dẫn ảnh logo", example = "/uploads/products/logo.png")
    private String logoUrl;

    @NotNull(message = "Ngưỡng tồn kho thấp không được để trống")
    @Min(value = 0, message = "Ngưỡng tồn kho thấp phải lớn hơn hoặc bằng 0")
    @Schema(description = "Ngưỡng cảnh báo tồn kho thấp", example = "10")
    private Integer lowStockThreshold;

    @NotNull(message = "Phí vận chuyển mặc định không được để trống")
    @Min(value = 0, message = "Phí vận chuyển mặc định phải lớn hơn hoặc bằng 0")
    @Schema(description = "Phí vận chuyển mặc định (VND)", example = "30000")
    private Long defaultShippingFee;

    @NotNull(message = "Ngưỡng miễn phí vận chuyển không được để trống")
    @Min(value = 0, message = "Ngưỡng miễn phí vận chuyển phải lớn hơn hoặc bằng 0")
    @Schema(description = "Ngưỡng miễn phí vận chuyển (VND)", example = "500000")
    private Long freeShippingThreshold;
}
