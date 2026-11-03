package com.shop.backend.common.converter;

import com.shop.backend.common.model.DataSourceType;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DataSourceTypeConverter extends AbstractEnumConverter<DataSourceType> {
    public DataSourceTypeConverter() {
        super(DataSourceType.class);
    }
}
