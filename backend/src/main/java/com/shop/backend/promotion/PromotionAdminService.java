package com.shop.backend.promotion;

import com.shop.backend.common.dto.PageResponse;
import com.shop.backend.common.model.DiscountType;
import com.shop.backend.promotion.dto.PromotionCreateRequest;
import com.shop.backend.promotion.dto.PromotionListItemResponse;
import com.shop.backend.promotion.dto.PromotionResponse;
import com.shop.backend.promotion.dto.PromotionStatusRequest;
import com.shop.backend.promotion.dto.PromotionUpdateRequest;

public interface PromotionAdminService {

    PageResponse<PromotionListItemResponse> getPromotions(
            String keyword,
            DiscountType discountType,
            Boolean isActive,
            PromotionTimeStatus status,
            Long productId,
            int page,
            int size
    );

    PromotionResponse getPromotionById(Long id);

    PromotionResponse createPromotion(PromotionCreateRequest request);

    PromotionResponse updatePromotion(Long id, PromotionUpdateRequest request);

    PromotionResponse updatePromotionStatus(Long id, PromotionStatusRequest request);

    void deletePromotion(Long id);
}
