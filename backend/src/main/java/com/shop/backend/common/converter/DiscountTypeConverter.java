package com.shop.backend.common.converter;

import com.shop.backend.common.model.DiscountType;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DiscountTypeConverter extends AbstractEnumConverter<DiscountType> {
    public DiscountTypeConverter() {
        super(DiscountType.class);
    }
}
