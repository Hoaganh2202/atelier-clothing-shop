package com.shop.backend.promotion;

import com.shop.backend.common.dto.PageResponse;
import com.shop.backend.common.exception.BadRequestException;
import com.shop.backend.common.exception.ResourceNotFoundException;
import com.shop.backend.common.model.DiscountType;
import com.shop.backend.product.ProductAdminService;
import com.shop.backend.product.dto.ProductDetailResponse;
import com.shop.backend.product.dto.ProductImageResponse;
import com.shop.backend.promotion.dto.PromotionCreateRequest;
import com.shop.backend.promotion.dto.PromotionListItemResponse;
import com.shop.backend.promotion.dto.PromotionProductItemResponse;
import com.shop.backend.promotion.dto.PromotionResponse;
import com.shop.backend.promotion.dto.PromotionStatusRequest;
import com.shop.backend.promotion.dto.PromotionUpdateRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class PromotionAdminServiceImpl implements PromotionAdminService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_PRODUCT_IDS = 100;
    private static final long MAX_PERCENT = 100L;

    private final PromotionRepository promotionRepository;
    private final PromotionProductRepository promotionProductRepository;
    private final ProductAdminService productAdminService;
    private final Clock clock;

    public PromotionAdminServiceImpl(
            PromotionRepository promotionRepository,
            PromotionProductRepository promotionProductRepository,
            ProductAdminService productAdminService,
            @Autowired(required = false) Clock clock) {
        this.promotionRepository = promotionRepository;
        this.promotionProductRepository = promotionProductRepository;
        this.productAdminService = productAdminService;
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
    }

    @Override
    public PageResponse<PromotionListItemResponse> getPromotions(
            String keyword,
            DiscountType discountType,
            Boolean isActive,
            PromotionTimeStatus status,
            Long productId,
            int page,
            int size) {
        LocalDateTime now = LocalDateTime.now(clock);
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "id"));

        Specification<Promotion> spec = PromotionSpecifications.byFilter(
                keyword, discountType, isActive, status, productId, now);
        Page<Promotion> pageResult = promotionRepository.findAll(spec, pageable);

        List<Long> promotionIds = pageResult.getContent().stream()
                .map(Promotion::getId)
                .toList();

        Map<Long, Long> countMap = Collections.emptyMap();
        if (!promotionIds.isEmpty()) {
            List<PromotionProductCountProjection> counts =
                    promotionProductRepository.countProductsByPromotionIds(promotionIds);
            countMap = counts.stream().collect(
                    Collectors.toMap(
                            PromotionProductCountProjection::getPromotionId,
                            PromotionProductCountProjection::getProductCount));
        }

        Map<Long, Long> finalCountMap = countMap;
        Page<PromotionListItemResponse> responsePage = pageResult.map(promotion ->
                PromotionListItemResponse.builder()
                        .id(promotion.getId())
                        .name(promotion.getName())
                        .discountType(promotion.getDiscountType())
                        .discountValue(toLong(promotion.getDiscountValue()))
                        .startAt(promotion.getStartAt())
                        .endAt(promotion.getEndAt())
                        .isActive(promotion.getIsActive())
                        .status(resolveTimeStatus(promotion, now))
                        .productCount(finalCountMap.getOrDefault(promotion.getId(), 0L))
                        .build());

        return PageResponse.of(responsePage);
    }

    @Override
    public PromotionResponse getPromotionById(Long id) {
        Promotion promotion = findPromotion(id);
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> productIds = promotionProductRepository.findProductIdsByPromotionId(id);
        List<PromotionProductItemResponse> productItems = loadProductItems(productIds);

        return toDetailResponse(promotion, productItems, now);
    }

    @Override
    @Transactional
    public PromotionResponse createPromotion(PromotionCreateRequest request) {
        validateCommonRules(
                request.getName(),
                request.getDiscountType(),
                request.getDiscountValue(),
                request.getStartAt(),
                request.getEndAt());

        List<Long> distinctProductIds = validateAndCleanProductIds(request.getProductIds());

        Promotion promotion = Promotion.builder()
                .name(request.getName().trim())
                .discountType(request.getDiscountType())
                .discountValue(BigDecimal.valueOf(request.getDiscountValue()))
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        Promotion saved = promotionRepository.save(promotion);

        if (!distinctProductIds.isEmpty()) {
            List<PromotionProduct> links = distinctProductIds.stream()
                    .map(pid -> new PromotionProduct(new PromotionProductId(pid, saved.getId())))
                    .toList();
            promotionProductRepository.saveAll(links);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        List<PromotionProductItemResponse> productItems = loadProductItems(distinctProductIds);
        return toDetailResponse(saved, productItems, now);
    }

    @Override
    @Transactional
    public PromotionResponse updatePromotion(Long id, PromotionUpdateRequest request) {
        Promotion promotion = findPromotion(id);

        validateCommonRules(
                request.getName(),
                request.getDiscountType(),
                request.getDiscountValue(),
                request.getStartAt(),
                request.getEndAt());

        List<Long> distinctProductIds = validateAndCleanProductIds(request.getProductIds());

        promotion.setName(request.getName().trim());
        promotion.setDiscountType(request.getDiscountType());
        promotion.setDiscountValue(BigDecimal.valueOf(request.getDiscountValue()));
        promotion.setStartAt(request.getStartAt());
        promotion.setEndAt(request.getEndAt());
        if (request.getIsActive() != null) {
            promotion.setIsActive(request.getIsActive());
        }

        Promotion saved = promotionRepository.save(promotion);

        List<Long> currentProductIds = promotionProductRepository.findProductIdsByPromotionId(id);
        Set<Long> currentSet = new HashSet<>(currentProductIds);
        Set<Long> newSet = new HashSet<>(distinctProductIds);

        Set<Long> toRemove = new HashSet<>(currentSet);
        toRemove.removeAll(newSet);

        Set<Long> toAdd = new HashSet<>(newSet);
        toAdd.removeAll(currentSet);

        if (!toRemove.isEmpty()) {
            promotionProductRepository.deleteByPromotionIdAndProductIdIn(id, toRemove);
        }
        if (!toAdd.isEmpty()) {
            List<PromotionProduct> newLinks = toAdd.stream()
                    .map(pid -> new PromotionProduct(new PromotionProductId(pid, id)))
                    .toList();
            promotionProductRepository.saveAll(newLinks);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        List<PromotionProductItemResponse> productItems = loadProductItems(distinctProductIds);
        return toDetailResponse(saved, productItems, now);
    }

    @Override
    @Transactional
    public PromotionResponse updatePromotionStatus(Long id, PromotionStatusRequest request) {
        Promotion promotion = findPromotion(id);
        promotion.setIsActive(request.getIsActive());
        Promotion saved = promotionRepository.save(promotion);

        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> productIds = promotionProductRepository.findProductIdsByPromotionId(id);
        List<PromotionProductItemResponse> productItems = loadProductItems(productIds);
        return toDetailResponse(saved, productItems, now);
    }

    @Override
    @Transactional
    public void deletePromotion(Long id) {
        Promotion promotion = findPromotion(id);
        promotionProductRepository.deleteByPromotionId(id);
        promotionRepository.delete(promotion);
    }

    private Promotion findPromotion(Long id) {
        return promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khuyến mãi với ID " + id));
    }

    private void validateCommonRules(
            String name,
            DiscountType discountType,
            Long discountValue,
            LocalDateTime startAt,
            LocalDateTime endAt) {
        if (!StringUtils.hasText(name) || name.trim().isEmpty()) {
            throw new BadRequestException("Tên khuyến mãi không được để trống");
        }
        if (name.trim().length() > 150) {
            throw new BadRequestException("Tên khuyến mãi tối đa 150 ký tự");
        }
        if (discountType == null) {
            throw new BadRequestException("Loại giảm giá không được để trống");
        }
        if (discountValue == null || discountValue <= 0) {
            throw new BadRequestException("Giá trị giảm phải lớn hơn 0");
        }
        if (discountType == DiscountType.PERCENT && discountValue > MAX_PERCENT) {
            throw new BadRequestException("Giá trị giảm theo phần trăm phải từ 1 đến 100");
        }
        if (startAt == null) {
            throw new BadRequestException("Thời gian bắt đầu không được để trống");
        }
        if (endAt == null) {
            throw new BadRequestException("Thời gian kết thúc không được để trống");
        }
        if (!endAt.isAfter(startAt)) {
            throw new BadRequestException("Thời gian kết thúc phải sau thời gian bắt đầu");
        }
    }

    private List<Long> validateAndCleanProductIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> distinctIds = productIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (distinctIds.size() > MAX_PRODUCT_IDS) {
            throw new BadRequestException("Danh sách sản phẩm áp dụng không được vượt quá " + MAX_PRODUCT_IDS + " sản phẩm");
        }

        List<Long> missingIds = new ArrayList<>();
        for (Long pid : distinctIds) {
            try {
                productAdminService.getProductById(pid);
            } catch (ResourceNotFoundException ex) {
                missingIds.add(pid);
            }
        }

        if (!missingIds.isEmpty()) {
            String missingStr = missingIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
            throw new BadRequestException("Không tìm thấy các sản phẩm với ID: " + missingStr);
        }

        return distinctIds;
    }

    private List<PromotionProductItemResponse> loadProductItems(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<PromotionProductItemResponse> items = new ArrayList<>();
        for (Long pid : productIds) {
            try {
                ProductDetailResponse detail = productAdminService.getProductById(pid);
                String primaryImage = null;
                if (detail.getImages() != null && !detail.getImages().isEmpty()) {
                    primaryImage = detail.getImages().stream()
                            .filter(img -> Boolean.TRUE.equals(img.getIsPrimary()))
                            .map(ProductImageResponse::getUrl)
                            .findFirst()
                            .orElse(detail.getImages().get(0).getUrl());
                }

                items.add(PromotionProductItemResponse.builder()
                        .id(detail.getId())
                        .name(detail.getName())
                        .slug(detail.getSlug())
                        .basePrice(detail.getBasePrice())
                        .isActive(detail.getIsActive())
                        .primaryImageUrl(primaryImage)
                        .build());
            } catch (ResourceNotFoundException ignored) {
                // Ignore missing product snapshots if any were removed externally
            }
        }
        return items;
    }

    private PromotionTimeStatus resolveTimeStatus(Promotion promotion, LocalDateTime now) {
        if (promotion.getStartAt().isAfter(now)) {
            return PromotionTimeStatus.SCHEDULED;
        }
        if (promotion.getEndAt().isBefore(now)) {
            return PromotionTimeStatus.EXPIRED;
        }
        return PromotionTimeStatus.RUNNING;
    }

    private Long toLong(BigDecimal value) {
        return value != null ? value.longValue() : null;
    }

    private PromotionResponse toDetailResponse(
            Promotion promotion,
            List<PromotionProductItemResponse> products,
            LocalDateTime now) {
        return PromotionResponse.builder()
                .id(promotion.getId())
                .name(promotion.getName())
                .discountType(promotion.getDiscountType())
                .discountValue(toLong(promotion.getDiscountValue()))
                .startAt(promotion.getStartAt())
                .endAt(promotion.getEndAt())
                .isActive(promotion.getIsActive())
                .status(resolveTimeStatus(promotion, now))
                .productCount((long) products.size())
                .products(products)
                .build();
    }
}
