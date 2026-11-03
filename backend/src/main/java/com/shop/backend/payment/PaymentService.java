package com.shop.backend.payment;

import com.shop.backend.payment.dto.PaymentProcessResult;

public interface PaymentService {

    PaymentProcessResult processPaymentSuccess(Long orderId, String transactionCode, PaymentMethod method);

    PaymentProcessResult processPaymentFailure(Long orderId, String transactionCode, PaymentMethod method, String failureReason);

    PaymentProcessResult processCodPaymentCompletion(Long orderId);

    PaymentProcessResult retryInvoiceCreation(Long orderId);
}
