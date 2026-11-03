package com.shop.backend.product;

import java.math.BigDecimal;

public interface PriceRangeProjection {
    BigDecimal getMinPrice();
    BigDecimal getMaxPrice();
}
