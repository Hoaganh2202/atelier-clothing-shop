package com.shop.backend.inventory;

import com.shop.backend.common.dto.PageResponse;
import com.shop.backend.common.exception.BadRequestException;
import com.shop.backend.common.exception.ConflictException;
import com.shop.backend.common.exception.ResourceNotFoundException;
import com.shop.backend.inventory.dto.InventoryStockUpdateRequest;
import com.shop.backend.inventory.dto.InventorySummaryAggregate;
import com.shop.backend.inventory.dto.InventorySummaryResponse;
import com.shop.backend.inventory.dto.InventoryVariantProjection;
import com.shop.backend.inventory.dto.InventoryVariantResponse;
import com.shop.backend.settings.StoreSettingsReader;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class InventoryAdminServiceImpl implements InventoryAdminService {

    public static final int DEFAULT_LOW_STOCK_THRESHOLD = 10;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MIN_THRESHOLD = 1;
    private static final int MAX_THRESHOLD = 1000;

    private static final Map<String, String> SORT_PROPERTY_MAPPING = Map.of(
            "stock", "stock",
            "id", "id",
            "productName", "p.name",
            "sku", "sku"
    );

    private final InventoryVariantRepository inventoryVariantRepository;
    private final StoreSettingsReader storeSettingsReader;

    public InventoryAdminServiceImpl(InventoryVariantRepository inventoryVariantRepository) {
        this(inventoryVariantRepository, null);
    }

    @Autowired
    public InventoryAdminServiceImpl(
            InventoryVariantRepository inventoryVariantRepository,
            StoreSettingsReader storeSettingsReader
    ) {
        this.inventoryVariantRepository = inventoryVariantRepository;
        this.storeSettingsReader = storeSettingsReader;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryVariantResponse> getVariants(
            String keyword,
            Long categoryId,
            String size,
            String color,
            String stockStatus,
            Integer threshold,
            Boolean productActive,
            int page,
            int pageSize,
            String sort
    ) {
        int safeThreshold = resolveThreshold(threshold);
        InventoryStockStatus statusEnum = InventoryStockStatus.fromString(stockStatus);
        String safeKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        String safeSize = StringUtils.hasText(size) ? size.trim() : null;
        String safeColor = StringUtils.hasText(color) ? color.trim() : null;

        Pageable pageable = resolvePageable(page, pageSize, sort);

        Page<InventoryVariantProjection> projectionPage = inventoryVariantRepository.findVariantsByFilter(
                safeKeyword,
                categoryId,
                safeSize,
                safeColor,
                statusEnum.name(),
                safeThreshold,
                productActive,
                pageable
        );

        Page<InventoryVariantResponse> responsePage = projectionPage.map(
                projection -> toVariantResponse(projection, safeThreshold)
        );

        return PageResponse.of(responsePage);
    }

    @Override
    @Transactional(readOnly = true)
    public InventorySummaryResponse getSummary(Integer threshold) {
        int safeThreshold = resolveThreshold(threshold);
        InventorySummaryAggregate agg = inventoryVariantRepository.getInventorySummary(safeThreshold);

        return InventorySummaryResponse.builder()
                .totalVariants(agg.getTotalVariants())
                .totalStock(agg.getTotalStock())
                .outOfStockCount(agg.getOutOfStockCount())
                .lowStockCount(agg.getLowStockCount())
                .inStockCount(agg.getInStockCount())
                .build();
    }

    @Override
    @Transactional
    public InventoryVariantResponse updateStock(Long id, InventoryStockUpdateRequest request) {
        int affected = inventoryVariantRepository.compareAndSwapStock(
                id,
                request.getNewStock(),
                request.getExpectedStock()
        );

        if (affected == 1) {
            InventoryVariantProjection projection = inventoryVariantRepository.findVariantProjectionById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biến thể với id: " + id));
            return toVariantResponse(projection, resolveThreshold(null));
        }

        boolean exists = inventoryVariantRepository.existsById(id);
        if (!exists) {
            throw new ResourceNotFoundException("Không tìm thấy biến thể với id: " + id);
        }

        throw new ConflictException("Tồn kho đã bị thay đổi bởi thao tác khác. Hãy tải lại dữ liệu rồi thử lại.");
    }

    private int resolveThreshold(Integer threshold) {
        if (threshold == null) {
            return storeSettingsReader != null
                    ? storeSettingsReader.getLowStockThreshold()
                    : DEFAULT_LOW_STOCK_THRESHOLD;
        }
        if (threshold < MIN_THRESHOLD || threshold > MAX_THRESHOLD) {
            throw new BadRequestException("Ngưỡng tồn kho phải nằm trong khoảng từ " + MIN_THRESHOLD + " đến " + MAX_THRESHOLD);
        }
        return threshold;
    }

    private Pageable resolvePageable(int page, int pageSize, String sort) {
        int safePage = Math.max(page, 0);
        int safeSize = pageSize > 0 ? Math.min(pageSize, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;

        Sort resolvedSort = resolveSort(sort);
        return PageRequest.of(safePage, safeSize, resolvedSort);
    }

    private Sort resolveSort(String sort) {
        if (!StringUtils.hasText(sort)) {
            return Sort.by(Sort.Direction.ASC, "stock").and(Sort.by(Sort.Direction.ASC, "id"));
        }

        String[] parts = sort.split(",");
        String property = parts[0].trim();
        String direction = parts.length > 1 ? parts[1].trim().toLowerCase() : "asc";

        if (!SORT_PROPERTY_MAPPING.containsKey(property)) {
            throw new BadRequestException("Tiêu chí sắp xếp không hợp lệ: " + property);
        }

        if (!"asc".equals(direction) && !"desc".equals(direction)) {
            throw new BadRequestException("Chiều sắp xếp không hợp lệ: " + direction);
        }

        String mappedProperty = SORT_PROPERTY_MAPPING.get(property);
        Sort.Direction sortDirection = "desc".equals(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;

        Sort resultSort = Sort.by(sortDirection, mappedProperty);
        if (!"id".equals(property)) {
            resultSort = resultSort.and(Sort.by(Sort.Direction.ASC, "id"));
        }

        return resultSort;
    }

    private InventoryVariantResponse toVariantResponse(InventoryVariantProjection projection, int threshold) {
        long priceVnd = projection.getPrice() != null ? projection.getPrice().longValue() : 0L;
        int stock = projection.getStock() != null ? projection.getStock() : 0;
        InventoryStockStatus stockStatus = InventoryStockStatus.calculateStatus(stock, threshold);

        return InventoryVariantResponse.builder()
                .id(projection.getId())
                .productId(projection.getProductId())
                .productName(projection.getProductName())
                .productIsActive(projection.getProductIsActive())
                .primaryImageUrl(projection.getPrimaryImageUrl())
                .categoryName(projection.getCategoryName())
                .sku(projection.getSku())
                .size(projection.getSize())
                .color(projection.getColor())
                .stock(stock)
                .price(priceVnd)
                .stockStatus(stockStatus)
                .build();
    }
}
