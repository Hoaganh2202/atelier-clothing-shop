package com.shop.backend.order.dto;

import com.shop.backend.order.OrderStatus;
import com.shop.backend.payment.PaymentMethod;
import com.shop.backend.payment.PaymentStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

@Builder
public record CustomerOrderDetailResponse(
        Long orderId,
        OrderStatus status,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        Long subtotal,
        Long discountAmount,
        Long shippingFee,
        Long totalAmount,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        LocalDateTime paidAt,
        String receiverName,
        String receiverPhone,
        String addressLine,
        String ward,
        String district,
        String province,
        String note,
        List<CustomerOrderItemResponse> items,
        boolean canCancel
) {}
