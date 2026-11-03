package com.shop.backend.product;

public interface ProductVariantService {

    ProductVariant getVariantEntity(Long variantId);

    int deductStock(Long variantId, Integer quantity);
}
