package com.shop.backend.einvoice.converter;

import com.shop.backend.common.converter.AbstractEnumConverter;
import com.shop.backend.einvoice.model.InvoiceItemType;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class InvoiceItemTypeConverter extends AbstractEnumConverter<InvoiceItemType> {
    public InvoiceItemTypeConverter() {
        super(InvoiceItemType.class);
    }
}
