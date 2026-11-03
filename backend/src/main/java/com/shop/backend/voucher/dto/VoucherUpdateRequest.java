package com.shop.backend.voucher.dto;

import com.shop.backend.common.model.DiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class VoucherUpdateRequest {

    public static final long MAX_MONEY = 9_999_999_999L;

    @NotNull(message = "Loại giảm giá không được để trống")
    @Schema(description = "PERCENT: giảm theo phần trăm; AMOUNT: giảm số tiền cố định (VND)")
    private DiscountType discountType;

    @NotNull(message = "Giá trị giảm không được để trống")
    @Min(value = 1, message = "Giá trị giảm phải lớn hơn 0")
    @Max(value = MAX_MONEY, message = "Giá trị giảm vượt quá giới hạn cho phép")
    @Schema(description = "PERCENT: số nguyên từ 1 đến 100; AMOUNT: số tiền VND lớn hơn 0")
    private Long discountValue;

    @Min(value = 0, message = "Đơn tối thiểu không được âm")
    @Max(value = MAX_MONEY, message = "Đơn tối thiểu vượt quá giới hạn cho phép")
    @Schema(description = "Giá trị đơn tối thiểu (VND). null = không áp dụng", nullable = true)
    private Long minOrder;

    @Min(value = 1, message = "Mức giảm tối đa phải lớn hơn 0")
    @Max(value = MAX_MONEY, message = "Mức giảm tối đa vượt quá giới hạn cho phép")
    @Schema(description = "Mức giảm tối đa (VND), chỉ dùng cho PERCENT; gửi cho AMOUNT sẽ bị từ chối. null = không giới hạn", nullable = true)
    private Long maxDiscount;

    @Min(value = 1, message = "Tổng số lượt dùng phải từ 1 trở lên")
    @Schema(description = "Tổng số lượt dùng. null = không giới hạn", nullable = true)
    private Integer usageLimit;

    @Min(value = 1, message = "Số lượt dùng mỗi khách phải từ 1 trở lên")
    @Schema(description = "Số lượt dùng tối đa mỗi khách. Không gửi = 1", nullable = true)
    private Integer perUserLimit;

    @NotNull(message = "Thời gian bắt đầu không được để trống")
    @Schema(description = "Giờ server, ISO local không múi giờ, ví dụ 2026-01-31T08:00:00", type = "string", format = "date-time")
    private LocalDateTime startAt;

    @NotNull(message = "Thời gian kết thúc không được để trống")
    @Schema(description = "Khi tạo mới phải ở tương lai và sau startAt", type = "string", format = "date-time")
    private LocalDateTime endAt;

    @Schema(description = "Tạo mới: không gửi = bật. Cập nhật: không gửi = giữ nguyên", nullable = true)
    private Boolean isActive;
}
