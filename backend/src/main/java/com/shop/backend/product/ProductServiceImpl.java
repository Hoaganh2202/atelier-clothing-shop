package com.shop.backend.product;

import com.shop.backend.common.dto.PageResponse;
import com.shop.backend.common.exception.BadRequestException;
import com.shop.backend.common.exception.ResourceNotFoundException;
import com.shop.backend.common.util.SlugUtils;
import com.shop.backend.product.dto.FilterOptionsResponse;
import com.shop.backend.product.dto.ProductCardResponse;
import com.shop.backend.product.dto.ProductCategoryResponse;
import com.shop.backend.product.dto.ProductImageResponse;
import com.shop.backend.product.dto.ProductPublicDetailResponse;
import com.shop.backend.product.dto.ProductVariantPublicResponse;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private static final Set<String> ALLOWED_SORTS = Set.of("newest", "price_asc", "price_desc");

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository imageRepository;
    private final CategoryRepository categoryRepository;

    @Override
    public PageResponse<ProductCardResponse> getProducts(
            String keyword,
            Long categoryId,
            Long minPrice,
            Long maxPrice,
            List<String> sizes,
            List<String> colors,
            String sort,
            int page,
            int size
    ) {
        if (page < 0) {
            throw new BadRequestException("Số trang không hợp lệ, phải lớn hơn hoặc bằng 0");
        }
        if (size < 1 || size > 50) {
            throw new BadRequestException("Kích thước trang không hợp lệ, phải từ 1 đến 50");
        }
        if (minPrice != null && minPrice < 0) {
            throw new BadRequestException("Giá tối thiểu không hợp lệ, phải lớn hơn hoặc bằng 0");
        }
        if (maxPrice != null && maxPrice < 0) {
            throw new BadRequestException("Giá tối đa không hợp lệ, phải lớn hơn hoặc bằng 0");
        }
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new BadRequestException("Giá tối thiểu không được lớn hơn giá tối đa");
        }

        String sortKey = StringUtils.hasText(sort) ? sort.trim().toLowerCase() : "newest";
        if (!ALLOWED_SORTS.contains(sortKey)) {
            throw new BadRequestException("Kiểu sắp xếp không hợp lệ: '" + sort + "'. Chỉ chấp nhận: newest, price_asc, price_desc");
        }

        final Set<Long> targetCategoryIds;
        if (categoryId != null) {
            targetCategoryIds = getCategoryAndDescendantIds(categoryId);
        } else {
            targetCategoryIds = getAllActiveCategoryIds();
        }

        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Only active products
            predicates.add(cb.equal(root.get("isActive"), true));

            // 2. Category scope (including descendants)
            if (targetCategoryIds.isEmpty()) {
                predicates.add(cb.disjunction());
            } else {
                predicates.add(root.get("category").get("id").in(targetCategoryIds));
            }

            // 3. Keyword search (unaccented matching name and slug)
            if (StringUtils.hasText(keyword)) {
                String trimmed = keyword.trim();
                String escaped = trimmed.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").toLowerCase();
                Predicate nameMatch = cb.like(cb.lower(root.get("name")), "%" + escaped + "%", '\\');
                Predicate slugMatch = cb.like(cb.lower(root.get("slug")), "%" + escaped + "%", '\\');

                String slugKeyword = SlugUtils.toSlug(trimmed);
                if (StringUtils.hasText(slugKeyword) && !slugKeyword.equals(trimmed.toLowerCase())) {
                    String escapedSlugKw = slugKeyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                    Predicate slugKwMatch = cb.like(cb.lower(root.get("slug")), "%" + escapedSlugKw + "%", '\\');
                    predicates.add(cb.or(nameMatch, slugMatch, slugKwMatch));
                } else {
                    predicates.add(cb.or(nameMatch, slugMatch));
                }
            }

            // 4. Combined variant filtering (size + color + price on THE SAME variant)
            boolean hasVariantFilter = (sizes != null && !sizes.isEmpty())
                    || (colors != null && !colors.isEmpty())
                    || minPrice != null
                    || maxPrice != null;

            if (hasVariantFilter) {
                Subquery<Integer> subquery = query.subquery(Integer.class);
                Root<ProductVariant> pvRoot = subquery.from(ProductVariant.class);
                subquery.select(cb.literal(1));

                List<Predicate> pvPredicates = new ArrayList<>();
                pvPredicates.add(cb.equal(pvRoot.get("product"), root));

                if (sizes != null && !sizes.isEmpty()) {
                    pvPredicates.add(pvRoot.get("size").in(sizes));
                }

                if (colors != null && !colors.isEmpty()) {
                    pvPredicates.add(pvRoot.get("color").in(colors));
                }

                Expression<BigDecimal> effectivePriceExpr = cb.coalesce(pvRoot.get("price"), root.get("basePrice"));
                if (minPrice != null) {
                    pvPredicates.add(cb.greaterThanOrEqualTo(effectivePriceExpr, BigDecimal.valueOf(minPrice)));
                }
                if (maxPrice != null) {
                    pvPredicates.add(cb.lessThanOrEqualTo(effectivePriceExpr, BigDecimal.valueOf(maxPrice)));
                }

                subquery.where(pvPredicates.toArray(new Predicate[0]));
                predicates.add(cb.exists(subquery));
            }

            // 5. Dynamic ordering on data query
            if (query.getResultType() == Product.class) {
                if ("price_asc".equals(sortKey)) {
                    Subquery<BigDecimal> minPriceSubquery = query.subquery(BigDecimal.class);
                    Root<ProductVariant> pvSubRoot = minPriceSubquery.from(ProductVariant.class);
                    minPriceSubquery.select(cb.min(cb.coalesce(pvSubRoot.get("price"), root.get("basePrice"))));
                    minPriceSubquery.where(cb.equal(pvSubRoot.get("product"), root));
                    Expression<BigDecimal> effectiveMinPrice = cb.coalesce(minPriceSubquery, root.get("basePrice"));
                    query.orderBy(cb.asc(effectiveMinPrice), cb.desc(root.get("id")));
                } else if ("price_desc".equals(sortKey)) {
                    Subquery<BigDecimal> minPriceSubquery = query.subquery(BigDecimal.class);
                    Root<ProductVariant> pvSubRoot = minPriceSubquery.from(ProductVariant.class);
                    minPriceSubquery.select(cb.min(cb.coalesce(pvSubRoot.get("price"), root.get("basePrice"))));
                    minPriceSubquery.where(cb.equal(pvSubRoot.get("product"), root));
                    Expression<BigDecimal> effectiveMinPrice = cb.coalesce(minPriceSubquery, root.get("basePrice"));
                    query.orderBy(cb.desc(effectiveMinPrice), cb.desc(root.get("id")));
                } else {
                    query.orderBy(cb.desc(root.get("createdAt")), cb.desc(root.get("id")));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Pageable pageable = PageRequest.of(page, size, Sort.unsorted());
        Page<Product> pageResult = productRepository.findAll(spec, pageable);

        if (pageResult.isEmpty()) {
            return PageResponse.<ProductCardResponse>builder()
                    .content(Collections.emptyList())
                    .page(pageResult.getNumber())
                    .size(pageResult.getSize())
                    .totalElements(0)
                    .totalPages(0)
                    .build();
        }

        List<ProductCardResponse> cardResponses = mapToProductCardResponses(pageResult.getContent());

        return PageResponse.<ProductCardResponse>builder()
                .content(cardResponses)
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .build();
    }

    @Override
    public ProductPublicDetailResponse getProductBySlug(String slug) {
        if (!StringUtils.hasText(slug)) {
            throw new ResourceNotFoundException("Sản phẩm không tồn tại hoặc đã ngừng kinh doanh");
        }

        Product product = productRepository.findWithCategoryBySlug(slug.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với slug: " + slug));

        if (!Boolean.TRUE.equals(product.getIsActive())) {
            throw new ResourceNotFoundException("Sản phẩm không tồn tại hoặc đã ngừng kinh doanh");
        }

        Category category = product.getCategory();
        if (category == null || !isCategoryAndAncestorsActive(category)) {
            throw new ResourceNotFoundException("Sản phẩm không tồn tại hoặc đã ngừng kinh doanh");
        }

        List<ProductVariant> variants = variantRepository.findByProductId(product.getId());
        List<ProductVariantPublicResponse> variantResponses = variants.stream()
                .map(v -> ProductVariantPublicResponse.builder()
                        .id(v.getId())
                        .size(v.getSize())
                        .color(v.getColor())
                        .effectivePrice(v.getPrice() != null ? v.getPrice().longValue() : product.getBasePrice().longValue())
                        .stock(v.getStock())
                        .build())
                .sorted(Comparator.comparing(ProductVariantPublicResponse::getSize, new SizeComparator())
                        .thenComparing(ProductVariantPublicResponse::getColor, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(ProductVariantPublicResponse::getId))
                .toList();

        long minPrice;
        long maxPrice;
        boolean inStock;

        if (!variants.isEmpty()) {
            minPrice = variants.stream()
                    .mapToLong(v -> v.getPrice() != null ? v.getPrice().longValue() : product.getBasePrice().longValue())
                    .min()
                    .orElse(product.getBasePrice().longValue());
            maxPrice = variants.stream()
                    .mapToLong(v -> v.getPrice() != null ? v.getPrice().longValue() : product.getBasePrice().longValue())
                    .max()
                    .orElse(product.getBasePrice().longValue());
            inStock = variants.stream().mapToInt(ProductVariant::getStock).sum() > 0;
        } else {
            minPrice = product.getBasePrice().longValue();
            maxPrice = product.getBasePrice().longValue();
            inStock = false;
        }

        List<ProductImage> images = imageRepository.findByProductIdOrderBySortOrderAsc(product.getId());
        List<ProductImageResponse> imageResponses = images.stream()
                .sorted(Comparator.comparing(ProductImage::getIsPrimary, Comparator.reverseOrder())
                        .thenComparing(ProductImage::getSortOrder)
                        .thenComparing(ProductImage::getId))
                .map(img -> ProductImageResponse.builder()
                        .id(img.getId())
                        .url(img.getUrl())
                        .alt(img.getAlt())
                        .isPrimary(img.getIsPrimary())
                        .sortOrder(img.getSortOrder())
                        .color(img.getColor())
                        .build())
                .toList();

        ProductCategoryResponse categoryResponse = mapCategoryHierarchy(category);

        List<Product> relatedProducts = productRepository.findTop8ByCategoryIdAndIdNotAndIsActiveTrueOrderByCreatedAtDescIdDesc(
                category.getId(),
                product.getId()
        );
        List<ProductCardResponse> relatedCardResponses = mapToProductCardResponses(relatedProducts);

        return ProductPublicDetailResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .category(categoryResponse)
                .images(imageResponses)
                .variants(variantResponses)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .inStock(inStock)
                .relatedProducts(relatedCardResponses)
                .build();
    }

    @Override
    public FilterOptionsResponse getFilterOptions(Long categoryId) {
        final Set<Long> targetCategoryIds;
        if (categoryId != null) {
            targetCategoryIds = getCategoryAndDescendantIds(categoryId);
        } else {
            targetCategoryIds = getAllActiveCategoryIds();
        }

        if (targetCategoryIds.isEmpty()) {
            return FilterOptionsResponse.builder()
                    .sizes(Collections.emptyList())
                    .colors(Collections.emptyList())
                    .minPrice(0L)
                    .maxPrice(0L)
                    .build();
        }

        List<String> rawSizes = variantRepository.findDistinctSizesByCategoryIds(targetCategoryIds);
        List<String> colors = variantRepository.findDistinctColorsByCategoryIds(targetCategoryIds);
        PriceRangeProjection range = variantRepository.getPriceRangeByCategoryIds(targetCategoryIds);

        List<String> sortedSizes = new ArrayList<>(rawSizes);
        sortedSizes.sort(new SizeComparator());

        long minPrice = (range != null && range.getMinPrice() != null) ? range.getMinPrice().longValue() : 0L;
        long maxPrice = (range != null && range.getMaxPrice() != null) ? range.getMaxPrice().longValue() : 0L;

        return FilterOptionsResponse.builder()
                .sizes(sortedSizes)
                .colors(colors)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .build();
    }

    private Set<Long> getAllActiveCategoryIds() {
        List<Category> activeList = categoryRepository.findByIsActiveTrue();
        Map<Long, List<Category>> childrenByParent = activeList.stream()
                .filter(c -> c.getParent() != null)
                .collect(Collectors.groupingBy(c -> c.getParent().getId()));

        Set<Long> validIds = new HashSet<>();
        List<Category> roots = activeList.stream()
                .filter(c -> c.getParent() == null)
                .toList();

        for (Category root : roots) {
            collectActiveCategoryTree(root.getId(), childrenByParent, validIds);
        }
        return validIds;
    }

    private void collectActiveCategoryTree(Long parentId, Map<Long, List<Category>> childrenByParent, Set<Long> validIds) {
        validIds.add(parentId);
        List<Category> children = childrenByParent.getOrDefault(parentId, Collections.emptyList());
        for (Category child : children) {
            collectActiveCategoryTree(child.getId(), childrenByParent, validIds);
        }
    }

    private boolean isCategoryAndAncestorsActive(Category category) {
        Category curr = category;
        while (curr != null) {
            if (!Boolean.TRUE.equals(curr.getIsActive())) {
                return false;
            }
            curr = curr.getParent();
        }
        return true;
    }

    private Set<Long> getCategoryAndDescendantIds(Long categoryId) {
        Set<Long> allActiveCategoryIds = getAllActiveCategoryIds();
        if (!allActiveCategoryIds.contains(categoryId)) {
            throw new ResourceNotFoundException("Không tìm thấy danh mục với id: " + categoryId);
        }

        List<Category> activeCategories = categoryRepository.findByIsActiveTrue();
        Set<Long> result = new HashSet<>();
        result.add(categoryId);
        collectDescendants(categoryId, activeCategories, result);
        result.retainAll(allActiveCategoryIds);
        return result;
    }

    private void collectDescendants(Long parentId, List<Category> all, Set<Long> result) {
        for (Category cat : all) {
            if (cat.getParent() != null && parentId.equals(cat.getParent().getId())) {
                if (result.add(cat.getId())) {
                    collectDescendants(cat.getId(), all, result);
                }
            }
        }
    }

    private List<ProductCardResponse> mapToProductCardResponses(List<Product> products) {
        if (products.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> productIds = products.stream().map(Product::getId).toList();

        Map<Long, ProductVariantStatsProjection> statsMap = variantRepository.getVariantStatsByProductIds(productIds)
                .stream()
                .collect(Collectors.toMap(ProductVariantStatsProjection::getProductId, s -> s));

        List<ProductImage> allImages = imageRepository.findByProductIdIn(productIds);
        Map<Long, List<ProductImage>> imageMap = allImages.stream()
                .collect(Collectors.groupingBy(img -> img.getProduct().getId()));

        return products.stream().map(product -> {
            ProductVariantStatsProjection stats = statsMap.get(product.getId());

            long minPrice = product.getBasePrice().longValue();
            long maxPrice = product.getBasePrice().longValue();
            boolean inStock = false;

            if (stats != null) {
                if (stats.getMinPrice() != null) {
                    minPrice = stats.getMinPrice().longValue();
                }
                if (stats.getMaxPrice() != null) {
                    maxPrice = stats.getMaxPrice().longValue();
                }
                if (stats.getTotalStock() != null) {
                    inStock = stats.getTotalStock() > 0;
                }
            }

            List<ProductImage> prodImages = imageMap.getOrDefault(product.getId(), Collections.emptyList());
            String thumbnailUrl = null;
            if (!prodImages.isEmpty()) {
                thumbnailUrl = prodImages.stream()
                        .min(Comparator.comparing(ProductImage::getIsPrimary, Comparator.reverseOrder())
                                .thenComparing(ProductImage::getSortOrder)
                                .thenComparing(ProductImage::getId))
                        .map(ProductImage::getUrl)
                        .orElse(null);
            }

            return ProductCardResponse.builder()
                    .id(product.getId())
                    .name(product.getName())
                    .slug(product.getSlug())
                    .thumbnailUrl(thumbnailUrl)
                    .minPrice(minPrice)
                    .maxPrice(maxPrice)
                    .inStock(inStock)
                    .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                    .build();
        }).toList();
    }

    private ProductCategoryResponse mapCategoryHierarchy(Category category) {
        if (category == null) {
            return null;
        }

        ProductCategoryResponse parentResponse = null;
        if (category.getParent() != null) {
            parentResponse = ProductCategoryResponse.builder()
                    .id(category.getParent().getId())
                    .name(category.getParent().getName())
                    .slug(category.getParent().getSlug())
                    .parent(null)
                    .build();
        }

        return ProductCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .parent(parentResponse)
                .build();
    }
}
