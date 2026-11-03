package com.shop.backend.order.dto;

import com.shop.backend.order.OrderStatus;
import com.shop.backend.payment.PaymentMethod;
import com.shop.backend.payment.PaymentStatus;
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
public class CheckoutResponse {

    private Long orderId;
    private OrderStatus status;
    private Long subtotal;
    private Long discountAmount;
    private Long shippingFee;
    private Long totalAmount;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private String receiverName;
    private String receiverPhone;
    private String addressLine;
    private String ward;
    private String district;
    private String province;
    private String note;
    private String voucherCode;
    private String idempotencyKey;
    private LocalDateTime createdAt;
    private List<CheckoutItemResponse> items;
}
