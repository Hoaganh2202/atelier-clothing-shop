package com.shop.backend.product;

import java.math.BigDecimal;

public interface ProductVariantStatsProjection {
    Long getProductId();
    Long getTotalStock();
    BigDecimal getMinPrice();
    BigDecimal getMaxPrice();
}
