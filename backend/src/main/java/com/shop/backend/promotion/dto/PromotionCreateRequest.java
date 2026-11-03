package com.shop.backend.promotion.dto;

import com.shop.backend.common.model.DiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
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
public class PromotionCreateRequest {

    public static final long MAX_MONEY = 9_999_999_999L;

    @NotBlank(message = "Tên khuyến mãi không được để trống")
    @Size(max = 150, message = "Tên khuyến mãi tối đa 150 ký tự")
    @Schema(description = "Tên chương trình khuyến mãi", example = "Khuyến mãi chào hè")
    private String name;

    @NotNull(message = "Loại giảm giá không được để trống")
    @Schema(description = "PERCENT: giảm theo phần trăm (1-100); AMOUNT: giảm số tiền cố định (VND)")
    private DiscountType discountType;

    @NotNull(message = "Giá trị giảm không được để trống")
    @Min(value = 1, message = "Giá trị giảm phải lớn hơn 0")
    @Max(value = MAX_MONEY, message = "Giá trị giảm vượt quá giới hạn cho phép")
    @Schema(description = "PERCENT: số nguyên từ 1 đến 100; AMOUNT: số tiền VND lớn hơn 0", example = "20")
    private Long discountValue;

    @NotNull(message = "Thời gian bắt đầu không được để trống")
    @Schema(description = "Thời gian bắt đầu khuyến mãi", type = "string", format = "date-time")
    private LocalDateTime startAt;

    @NotNull(message = "Thời gian kết thúc không được để trống")
    @Schema(description = "Thời gian kết thúc khuyến mãi (phải sau thời gian bắt đầu)", type = "string", format = "date-time")
    private LocalDateTime endAt;

    @Schema(description = "Trạng thái kích hoạt (null = mặc định true)", nullable = true, defaultValue = "true")
    private Boolean isActive;

    @Schema(description = "Danh sách ID sản phẩm áp dụng (tối đa 100 ID, được phép rỗng hoặc null)", nullable = true)
    private List<Long> productIds;
}
