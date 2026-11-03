package com.shop.backend.product;

import com.shop.backend.product.dto.CategoryPublicResponse;
import java.util.List;

public interface CategoryService {
    List<CategoryPublicResponse> getCategoryTree();
}
