package com.shop.backend.order.dto;

import com.shop.backend.inventory.dto.ReturnItemCondition;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrderReturnReceiptItemRequest(
        @NotNull(message = "Mã chi tiết đơn hàng không được để trống")
        Long orderDetailId,

        @NotNull(message = "Số lượng tiếp nhận không được để trống")
        @Min(value = 1, message = "Số lượng tiếp nhận phải lớn hơn 0")
        Integer quantity,

        @NotNull(message = "Tình trạng sản phẩm tiếp nhận không được để trống")
        ReturnItemCondition condition,

        @Size(max = 500, message = "Lý do tiếp nhận tối đa 500 ký tự")
        String reason
) {}
