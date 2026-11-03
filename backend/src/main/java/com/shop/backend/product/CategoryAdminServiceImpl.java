package com.shop.backend.product;

import com.shop.backend.common.exception.BadRequestException;
import com.shop.backend.common.exception.ConflictException;
import com.shop.backend.common.exception.ResourceNotFoundException;
import com.shop.backend.common.util.SlugUtils;
import com.shop.backend.product.dto.CategoryProductCountProjection;
import com.shop.backend.product.dto.CategoryResponse;
import com.shop.backend.product.dto.CategoryStatusRequest;
import com.shop.backend.product.dto.CategoryUpsertRequest;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryAdminServiceImpl implements CategoryAdminService {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Override
    public List<CategoryResponse> getAllCategories() {
        List<Category> categories = categoryRepository.findAllWithParent();
        Map<Long, Long> productCountMap = productRepository.countProductsGroupedByCategoryId().stream()
                .collect(Collectors.toMap(
                        CategoryProductCountProjection::getCategoryId,
                        CategoryProductCountProjection::getProductCount,
                        (existing, replacement) -> existing
                ));

        return categories.stream()
                .map(category -> mapToCategoryResponse(category, productCountMap.getOrDefault(category.getId(), 0L)))
                .toList();
    }

    @Override
    public CategoryResponse getCategoryById(Long id) {
        Category category = findCategoryById(id);
        long productCount = productRepository.countProductsGroupedByCategoryId().stream()
                .filter(p -> p.getCategoryId().equals(id))
                .map(CategoryProductCountProjection::getProductCount)
                .findFirst()
                .orElse(0L);
        return mapToCategoryResponse(category, productCount);
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(CategoryUpsertRequest request) {
        String trimmedName = request.getName() != null ? request.getName().trim() : "";
        if (trimmedName.isEmpty()) {
            throw new BadRequestException("Tên danh mục không được để trống");
        }

        String slug = resolveSlug(request.getSlug(), trimmedName, null);
        Category parent = validateAndResolveParent(request.getParentId(), null);

        Category category = Category.builder()
                .name(trimmedName)
                .slug(slug)
                .parent(parent)
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        Category saved = categoryRepository.save(category);
        return mapToCategoryResponse(saved, 0L);
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryUpsertRequest request) {
        Category category = findCategoryById(id);

        String trimmedName = request.getName() != null ? request.getName().trim() : "";
        if (trimmedName.isEmpty()) {
            throw new BadRequestException("Tên danh mục không được để trống");
        }

        String slug = resolveSlug(request.getSlug(), trimmedName, id);
        Category parent = validateAndResolveParent(request.getParentId(), id);

        category.setName(trimmedName);
        category.setSlug(slug);
        category.setParent(parent);
        if (request.getIsActive() != null) {
            category.setIsActive(request.getIsActive());
        }

        Category updated = categoryRepository.save(category);
        long productCount = productRepository.countProductsGroupedByCategoryId().stream()
                .filter(p -> p.getCategoryId().equals(id))
                .map(CategoryProductCountProjection::getProductCount)
                .findFirst()
                .orElse(0L);

        return mapToCategoryResponse(updated, productCount);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        Category category = findCategoryById(id);

        if (categoryRepository.existsByParent_Id(id)) {
            throw new ConflictException("Không thể xóa danh mục vì còn danh mục con trực thuộc. Vui lòng xóa hoặc di chuyển danh mục con trước, hoặc chuyển sang trạng thái ngừng hoạt động.");
        }

        if (productRepository.existsByCategoryId(id)) {
            throw new ConflictException("Không thể xóa danh mục vì còn sản phẩm trực thuộc. Vui lòng xóa hoặc đổi danh mục cho sản phẩm trước, hoặc chuyển sang trạng thái ngừng hoạt động.");
        }

        categoryRepository.delete(category);
    }

    @Override
    @Transactional
    public CategoryResponse updateCategoryStatus(Long id, CategoryStatusRequest request) {
        Category category = findCategoryById(id);
        category.setIsActive(request.getIsActive());
        Category updated = categoryRepository.save(category);

        long productCount = productRepository.countProductsGroupedByCategoryId().stream()
                .filter(p -> p.getCategoryId().equals(id))
                .map(CategoryProductCountProjection::getProductCount)
                .findFirst()
                .orElse(0L);

        return mapToCategoryResponse(updated, productCount);
    }

    private Category findCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Danh mục không tồn tại với ID: " + id));
    }

    private String resolveSlug(String inputSlug, String name, Long existingId) {
        String slug;
        if (StringUtils.hasText(inputSlug)) {
            slug = inputSlug.trim().toLowerCase(Locale.ROOT);
            if (!SLUG_PATTERN.matcher(slug).matches()) {
                throw new BadRequestException("Slug chỉ được chứa chữ thường không dấu, số và dấu gạch ngang, không bắt đầu hay kết thúc bằng gạch ngang");
            }
        } else {
            slug = SlugUtils.toSlug(name);
            if (!StringUtils.hasText(slug)) {
                slug = "danh-muc";
            }
        }

        if (existingId == null) {
            if (categoryRepository.existsBySlugIgnoreCase(slug)) {
                throw new ConflictException("Slug '" + slug + "' đã tồn tại, vui lòng chọn slug khác.");
            }
        } else {
            if (categoryRepository.existsBySlugIgnoreCaseAndIdNot(slug, existingId)) {
                throw new ConflictException("Slug '" + slug + "' đã tồn tại, vui lòng chọn slug khác.");
            }
        }
        return slug;
    }

    private Category validateAndResolveParent(Long parentId, Long currentCategoryId) {
        if (parentId == null) {
            return null;
        }

        if (currentCategoryId != null && parentId.equals(currentCategoryId)) {
            throw new BadRequestException("Danh mục không thể làm cha của chính nó.");
        }

        Category parent = categoryRepository.findById(parentId)
                .orElseThrow(() -> new ResourceNotFoundException("Danh mục cha không tồn tại với ID: " + parentId));

        if (parent.getParent() != null) {
            throw new BadRequestException("Hệ thống chỉ hỗ trợ tối đa 2 cấp danh mục. Danh mục '" + parent.getName() + "' đã là danh mục con nên không thể làm danh mục cha.");
        }

        if (currentCategoryId != null && categoryRepository.existsByParent_Id(currentCategoryId)) {
            throw new BadRequestException("Danh mục này đang có danh mục con, không thể chuyển thành danh mục con của danh mục khác.");
        }

        return parent;
    }

    private CategoryResponse mapToCategoryResponse(Category category, Long productCount) {
        return CategoryResponse.builder()
                .id(category.getId())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .name(category.getName())
                .slug(category.getSlug())
                .isActive(category.getIsActive())
                .productCount(productCount != null ? productCount : 0L)
                .build();
    }
}
