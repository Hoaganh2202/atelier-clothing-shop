package com.shop.backend.order.dto;

import com.shop.backend.order.OrderStatus;
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
public class OrderAdminListItemResponse {

    private Long id;
    private String orderCode;
    private String customerName;
    private String customerPhone;
    private LocalDateTime createdAt;
    private String itemsSummary;
    private Integer itemCount;
    private Long totalAmount;
    private OrderStatus status;
}
