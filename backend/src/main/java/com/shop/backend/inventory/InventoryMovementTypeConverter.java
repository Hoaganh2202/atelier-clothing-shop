package com.shop.backend.inventory;

import com.shop.backend.common.converter.AbstractEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class InventoryMovementTypeConverter extends AbstractEnumConverter<InventoryMovementType> {

    public InventoryMovementTypeConverter() {
        super(InventoryMovementType.class);
    }
}
