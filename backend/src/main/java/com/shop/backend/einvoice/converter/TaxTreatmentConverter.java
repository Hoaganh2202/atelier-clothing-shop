package com.shop.backend.einvoice.converter;

import com.shop.backend.common.converter.AbstractEnumConverter;
import com.shop.backend.einvoice.model.TaxTreatment;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TaxTreatmentConverter extends AbstractEnumConverter<TaxTreatment> {
    public TaxTreatmentConverter() {
        super(TaxTreatment.class);
    }
}
