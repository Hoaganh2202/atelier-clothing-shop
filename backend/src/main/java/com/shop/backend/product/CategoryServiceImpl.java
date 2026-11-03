package com.shop.backend.product;

import com.shop.backend.product.dto.CategoryPublicResponse;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    public List<CategoryPublicResponse> getCategoryTree() {
        List<Category> activeCategories = categoryRepository.findByIsActiveTrue();

        Map<Long, List<Category>> childrenMap = activeCategories.stream()
                .filter(c -> c.getParent() != null)
                .collect(Collectors.groupingBy(c -> c.getParent().getId()));

        List<Category> topLevel = activeCategories.stream()
                .filter(c -> c.getParent() == null)
                .sorted(Comparator.comparing(Category::getId))
                .toList();

        return topLevel.stream()
                .map(cat -> mapToTreeResponse(cat, childrenMap))
                .toList();
    }

    private CategoryPublicResponse mapToTreeResponse(Category cat, Map<Long, List<Category>> childrenMap) {
        List<Category> children = childrenMap.getOrDefault(cat.getId(), Collections.emptyList());
        List<CategoryPublicResponse> childResponses = children.stream()
                .sorted(Comparator.comparing(Category::getId))
                .map(c -> mapToTreeResponse(c, childrenMap))
                .toList();

        return CategoryPublicResponse.builder()
                .id(cat.getId())
                .name(cat.getName())
                .slug(cat.getSlug())
                .children(childResponses)
                .build();
    }
}
