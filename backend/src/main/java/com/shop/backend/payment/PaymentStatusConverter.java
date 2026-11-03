package com.shop.backend.payment;

import com.shop.backend.common.converter.AbstractEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PaymentStatusConverter extends AbstractEnumConverter<PaymentStatus> {
    public PaymentStatusConverter() {
        super(PaymentStatus.class);
    }
}
