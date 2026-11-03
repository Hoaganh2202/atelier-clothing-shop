package com.shop.backend.shipping.dto;

import com.shop.backend.order.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingStatusUpdateRequest {

    @NotNull(message = "Trạng thái mới không được để trống")
    private OrderStatus status;
}
