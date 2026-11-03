package com.shop.backend.product;

import com.shop.backend.product.dto.CategoryResponse;
import com.shop.backend.product.dto.CategoryStatusRequest;
import com.shop.backend.product.dto.CategoryUpsertRequest;
import java.util.List;

public interface CategoryAdminService {

    List<CategoryResponse> getAllCategories();

    CategoryResponse getCategoryById(Long id);

    CategoryResponse createCategory(CategoryUpsertRequest request);

    CategoryResponse updateCategory(Long id, CategoryUpsertRequest request);

    void deleteCategory(Long id);

    CategoryResponse updateCategoryStatus(Long id, CategoryStatusRequest request);
}
