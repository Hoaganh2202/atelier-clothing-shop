package com.shop.backend.cart;

import com.shop.backend.common.converter.AbstractEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CartStatusConverter extends AbstractEnumConverter<CartStatus> {
    public CartStatusConverter() {
        super(CartStatus.class);
    }
}
