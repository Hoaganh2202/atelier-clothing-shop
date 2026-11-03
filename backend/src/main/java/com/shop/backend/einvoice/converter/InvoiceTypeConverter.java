package com.shop.backend.einvoice.converter;

import com.shop.backend.common.converter.AbstractEnumConverter;
import com.shop.backend.einvoice.model.InvoiceType;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class InvoiceTypeConverter extends AbstractEnumConverter<InvoiceType> {
    public InvoiceTypeConverter() {
        super(InvoiceType.class);
    }
}
