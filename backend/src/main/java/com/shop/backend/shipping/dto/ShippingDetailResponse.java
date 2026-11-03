package com.shop.backend.shipping.dto;

import com.shop.backend.order.OrderStatus;
import com.shop.backend.payment.PaymentMethod;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingDetailResponse {

    private Long orderId;
    private String orderCode;
    private OrderStatus status;
    private String receiverName;
    private String receiverPhone;
    private String addressLine;
    private String ward;
    private String district;
    private String province;
    private String fullAddress;
    private PaymentMethod paymentMethod;
    private Long codAmount;
    private Long shippingFee;
    private Long subtotal;
    private Long discountAmount;
    private Long totalAmount;
    private String customerNote;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private List<ShippingItemResponse> items;
}
