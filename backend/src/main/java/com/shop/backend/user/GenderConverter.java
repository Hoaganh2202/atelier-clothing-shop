package com.shop.backend.user;

import com.shop.backend.common.converter.AbstractEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class GenderConverter extends AbstractEnumConverter<Gender> {
    public GenderConverter() {
        super(Gender.class);
    }
}
