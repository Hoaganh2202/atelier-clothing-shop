package com.shop.backend.shipping.dto;

import com.shop.backend.order.OrderStatus;
import com.shop.backend.payment.PaymentMethod;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingListItemResponse {

    private Long orderId;
    private String orderCode;
    private OrderStatus status;
    private String receiverName;
    private String receiverPhone;
    private String fullAddress;
    private String province;
    private PaymentMethod paymentMethod;
    private Long codAmount;
    private Long shippingFee;
    private Long totalAmount;
    private int itemCount;
    private String itemsSummary;
    private String customerNote;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
