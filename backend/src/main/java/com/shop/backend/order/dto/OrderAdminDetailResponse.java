package com.shop.backend.order.dto;

import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.order.OrderStatus;
import com.shop.backend.payment.PaymentMethod;
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
public class OrderAdminDetailResponse {

    private Long id;
    private String orderCode;
    private OrderStatus status;
    private OrderStatus cancelledFromStatus;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private Long subtotal;
    private Long discountAmount;
    private Long shippingFee;
    private Long totalAmount;
    private PaymentMethod paymentMethod;
    private String receiverName;
    private String receiverPhone;
    private String addressLine;
    private String ward;
    private String district;
    private String province;
    private String note;
    private String salesChannel;
    private DataSourceType dataSource;
    private Long voucherId;
    private List<OrderItemAdminResponse> items;
    private List<OrderPaymentAdminResponse> payments;
}
