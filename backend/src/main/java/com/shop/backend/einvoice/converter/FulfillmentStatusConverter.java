package com.shop.backend.einvoice.converter;

import com.shop.backend.common.converter.AbstractEnumConverter;
import com.shop.backend.einvoice.model.FulfillmentStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class FulfillmentStatusConverter extends AbstractEnumConverter<FulfillmentStatus> {
    public FulfillmentStatusConverter() {
        super(FulfillmentStatus.class);
    }
}
