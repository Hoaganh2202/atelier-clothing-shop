package com.shop.backend.order.dto;

import com.shop.backend.payment.PaymentMethod;
import com.shop.backend.payment.PaymentStatus;
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
public class OrderPaymentAdminResponse {

    private Long id;
    private PaymentMethod method;
    private Long amount;
    private PaymentStatus status;
    private String transactionCode;
    private LocalDateTime paidAt;
}
