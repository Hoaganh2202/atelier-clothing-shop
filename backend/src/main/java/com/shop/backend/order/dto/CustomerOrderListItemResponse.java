package com.shop.backend.order.dto;

import com.shop.backend.order.OrderStatus;
import com.shop.backend.payment.PaymentMethod;
import com.shop.backend.payment.PaymentStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

@Builder
public record CustomerOrderListItemResponse(
        Long orderId,
        OrderStatus status,
        LocalDateTime createdAt,
        Long totalAmount,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        Integer lineCount,
        List<CustomerOrderPreviewItemResponse> previewItems,
        boolean canCancel
) {}
