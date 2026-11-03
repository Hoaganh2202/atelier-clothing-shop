package com.shop.backend.einvoice.converter;

import com.shop.backend.common.converter.AbstractEnumConverter;
import com.shop.backend.einvoice.model.OutboxStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class OutboxStatusConverter extends AbstractEnumConverter<OutboxStatus> {
    public OutboxStatusConverter() {
        super(OutboxStatus.class);
    }
}
