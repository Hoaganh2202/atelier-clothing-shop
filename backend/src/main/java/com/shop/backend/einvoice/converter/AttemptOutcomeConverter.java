package com.shop.backend.einvoice.converter;

import com.shop.backend.common.converter.AbstractEnumConverter;
import com.shop.backend.einvoice.model.AttemptOutcome;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AttemptOutcomeConverter extends AbstractEnumConverter<AttemptOutcome> {
    public AttemptOutcomeConverter() {
        super(AttemptOutcome.class);
    }
}
