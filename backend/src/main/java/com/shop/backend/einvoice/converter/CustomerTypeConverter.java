package com.shop.backend.einvoice.converter;

import com.shop.backend.common.converter.AbstractEnumConverter;
import com.shop.backend.einvoice.model.CustomerType;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CustomerTypeConverter extends AbstractEnumConverter<CustomerType> {
    public CustomerTypeConverter() {
        super(CustomerType.class);
    }
}
