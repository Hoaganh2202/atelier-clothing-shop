package com.shop.backend.product;

import com.shop.backend.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductVariantServiceImpl implements ProductVariantService {

    private final ProductVariantRepository productVariantRepository;

    @Override
    public ProductVariant getVariantEntity(Long variantId) {
        return productVariantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Biến thể sản phẩm không tồn tại"));
    }

    @Override
    @Transactional
    public int deductStock(Long variantId, Integer quantity) {
        return productVariantRepository.deductStock(variantId, quantity);
    }
}
