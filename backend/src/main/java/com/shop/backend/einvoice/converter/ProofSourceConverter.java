package com.shop.backend.einvoice.converter;

import com.shop.backend.common.converter.AbstractEnumConverter;
import com.shop.backend.einvoice.model.ProofSource;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ProofSourceConverter extends AbstractEnumConverter<ProofSource> {
    public ProofSourceConverter() {
        super(ProofSource.class);
    }
}
