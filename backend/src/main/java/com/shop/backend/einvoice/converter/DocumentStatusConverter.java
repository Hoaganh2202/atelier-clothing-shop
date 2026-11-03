package com.shop.backend.einvoice.converter;

import com.shop.backend.common.converter.AbstractEnumConverter;
import com.shop.backend.einvoice.model.DocumentStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DocumentStatusConverter extends AbstractEnumConverter<DocumentStatus> {
    public DocumentStatusConverter() {
        super(DocumentStatus.class);
    }
}
