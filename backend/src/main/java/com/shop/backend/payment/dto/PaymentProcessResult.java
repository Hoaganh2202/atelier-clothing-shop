package com.shop.backend.payment.dto;

import com.shop.backend.einvoice.model.Invoice;
import com.shop.backend.payment.Payment;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PaymentProcessResult {
    Payment payment;
    Invoice invoice;
    boolean invoiceCreated;
    String message;
}
