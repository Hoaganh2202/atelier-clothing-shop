package com.shop.backend.order;

import com.shop.backend.common.converter.AbstractEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class OrderReturnStatusConverter extends AbstractEnumConverter<OrderReturnStatus> {
    public OrderReturnStatusConverter() {
        super(OrderReturnStatus.class);
    }
}
