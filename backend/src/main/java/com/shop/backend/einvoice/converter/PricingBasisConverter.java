package com.shop.backend.einvoice.converter;

import com.shop.backend.common.converter.AbstractEnumConverter;
import com.shop.backend.einvoice.model.PricingBasis;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PricingBasisConverter extends AbstractEnumConverter<PricingBasis> {
    public PricingBasisConverter() {
        super(PricingBasis.class);
    }
}
