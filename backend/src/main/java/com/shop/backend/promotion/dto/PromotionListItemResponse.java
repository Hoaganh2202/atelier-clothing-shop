package com.shop.backend.promotion.dto;

import com.shop.backend.common.model.DiscountType;
import com.shop.backend.promotion.PromotionTimeStatus;
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
public class PromotionListItemResponse {

    @Schema(description = "ID khuyến mãi")
    private Long id;

    @Schema(description = "Tên chương trình khuyến mãi")
    private String name;

    @Schema(description = "Loại giảm giá (PERCENT / AMOUNT)")
    private DiscountType discountType;

    @Schema(description = "Giá trị giảm (phần trăm nguyên 1-100 hoặc số tiền VND)")
    private Long discountValue;

    @Schema(description = "Thời điểm bắt đầu")
    private LocalDateTime startAt;

    @Schema(description = "Thời điểm kết thúc")
    private LocalDateTime endAt;

    @Schema(description = "Trạng thái kích hoạt (bật/tắt)")
    private Boolean isActive;

    @Schema(description = "Trạng thái thời gian so với giờ hệ thống (SCHEDULED / RUNNING / EXPIRED)")
    private PromotionTimeStatus status;

    @Schema(description = "Số lượng sản phẩm áp dụng khuyến mãi này")
    private Long productCount;
}
