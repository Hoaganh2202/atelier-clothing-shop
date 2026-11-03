package com.shop.backend.einvoice.converter;

import com.shop.backend.common.converter.AbstractEnumConverter;
import com.shop.backend.einvoice.model.AdjustmentType;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AdjustmentTypeConverter extends AbstractEnumConverter<AdjustmentType> {
    public AdjustmentTypeConverter() {
        super(AdjustmentType.class);
    }
}
