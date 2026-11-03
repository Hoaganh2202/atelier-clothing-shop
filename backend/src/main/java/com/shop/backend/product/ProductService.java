package com.shop.backend.product;

import com.shop.backend.common.dto.PageResponse;
import com.shop.backend.product.dto.FilterOptionsResponse;
import com.shop.backend.product.dto.ProductCardResponse;
import com.shop.backend.product.dto.ProductPublicDetailResponse;
import java.util.List;

public interface ProductService {

    PageResponse<ProductCardResponse> getProducts(
            String keyword,
            Long categoryId,
            Long minPrice,
            Long maxPrice,
            List<String> sizes,
            List<String> colors,
            String sort,
            int page,
            int size
    );

    ProductPublicDetailResponse getProductBySlug(String slug);

    FilterOptionsResponse getFilterOptions(Long categoryId);
}
