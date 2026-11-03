package com.shop.backend.voucher.dto;

import com.shop.backend.common.model.DiscountType;
import com.shop.backend.voucher.VoucherTimeStatus;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VoucherResponse {

    private Long id;
    private String code;
    private DiscountType discountType;
    private Long discountValue;
    @Schema(nullable = true, description = "null = không áp dụng")
    private Long minOrder;
    @Schema(nullable = true, description = "null = không giới hạn mức giảm")
    private Long maxDiscount;
    @Schema(nullable = true, description = "null = không giới hạn lượt")
    private Integer usageLimit;
    private Integer perUserLimit;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Boolean isActive;
    @Schema(description = "Trạng thái theo thời gian so với giờ server, độc lập với isActive")
    private VoucherTimeStatus status;
}
