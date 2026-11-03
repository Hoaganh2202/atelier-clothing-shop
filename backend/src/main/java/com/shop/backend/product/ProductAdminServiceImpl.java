package com.shop.backend.product;

import com.shop.backend.common.dto.PageResponse;
import com.shop.backend.common.exception.BadRequestException;
import com.shop.backend.common.exception.ConflictException;
import com.shop.backend.common.exception.ResourceNotFoundException;
import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.common.util.SlugUtils;
import com.shop.backend.product.dto.BulkStatusRequest;
import com.shop.backend.product.dto.ImageMetadataUpdateRequest;
import com.shop.backend.product.dto.LowStockVariantResponse;
import com.shop.backend.product.dto.ProductAdminListItemResponse;
import com.shop.backend.product.dto.ProductDetailResponse;
import com.shop.backend.product.dto.ProductImageResponse;
import com.shop.backend.product.dto.ProductUpsertRequest;
import com.shop.backend.product.dto.ProductVariantResponse;
import com.shop.backend.settings.StoreSettingsReader;
import com.shop.backend.product.dto.VariantUpsertRequest;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.criteria.Predicate;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductAdminServiceImpl implements ProductAdminService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("name", "basePrice", "createdAt", "id");

    @Value("${app.upload.dir:uploads/products}")
    private String uploadDirProperty;

    private Path resolvedUploadPath;

    private final ProductRepository productRepository;
    private final StoreSettingsReader storeSettingsReader;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository imageRepository;
    private final CategoryRepository categoryRepository;

    @PostConstruct
    public void initUploadDirectory() {
        this.resolvedUploadPath = Paths.get(uploadDirProperty).toAbsolutePath().normalize();
        try {
            if (!Files.exists(this.resolvedUploadPath)) {
                Files.createDirectories(this.resolvedUploadPath);
            }
        } catch (IOException e) {
            log.error("Failed to initialize upload directory: {}", this.resolvedUploadPath, e);
        }
    }

    @Override
    public PageResponse<ProductAdminListItemResponse> getProducts(
            String keyword,
            Long categoryId,
            Boolean isActive,
            Pageable pageable
    ) {
        int pageNumber = Math.max(0, pageable.getPageNumber());
        int pageSize = Math.min(Math.max(1, pageable.getPageSize()), 100);

        for (Sort.Order order : pageable.getSort()) {
            if (!ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
                throw new BadRequestException(
                        "Trường sắp xếp không hợp lệ: '" + order.getProperty() +
                        "'. Chỉ chấp nhận: " + String.join(", ", ALLOWED_SORT_FIELDS));
            }
        }

        Pageable safePageable = PageRequest.of(pageNumber, pageSize, pageable.getSort());

        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(keyword)) {
                String trimmed = keyword.trim();
                String searchPattern = "%" + trimmed.toLowerCase() + "%";
                Predicate nameMatch = cb.like(cb.lower(root.get("name")), searchPattern);
                Predicate slugMatch = cb.like(cb.lower(root.get("slug")), searchPattern);

                String slugKeyword = SlugUtils.toSlug(trimmed);
                if (StringUtils.hasText(slugKeyword) && !slugKeyword.equals(trimmed.toLowerCase())) {
                    Predicate slugKeywordMatch = cb.like(cb.lower(root.get("slug")), "%" + slugKeyword + "%");
                    predicates.add(cb.or(nameMatch, slugMatch, slugKeywordMatch));
                } else {
                    predicates.add(cb.or(nameMatch, slugMatch));
                }
            }

            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }

            if (isActive != null) {
                predicates.add(cb.equal(root.get("isActive"), isActive));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Product> page = productRepository.findAll(spec, safePageable);
        if (page.isEmpty()) {
            return PageResponse.<ProductAdminListItemResponse>builder()
                    .content(java.util.Collections.emptyList())
                    .page(safePageable.getPageNumber())
                    .size(safePageable.getPageSize())
                    .totalElements(0)
                    .totalPages(0)
                    .build();
        }

        List<Long> productIds = page.getContent().stream()
                .map(Product::getId)
                .toList();

        Map<Long, ProductVariantStatsProjection> statsMap = variantRepository.getVariantStatsByProductIds(productIds)
                .stream()
                .collect(Collectors.toMap(ProductVariantStatsProjection::getProductId, s -> s));

        List<ProductImage> allImages = imageRepository.findByProductIdIn(productIds);
        Map<Long, String> primaryImageMap = allImages.stream()
                .filter(ProductImage::getIsPrimary)
                .collect(Collectors.toMap(
                        img -> img.getProduct().getId(),
                        ProductImage::getUrl,
                        (existing, replacement) -> existing
                ));

        allImages.stream()
                .collect(Collectors.groupingBy(img -> img.getProduct().getId()))
                .forEach((prodId, images) -> {
                    if (!primaryImageMap.containsKey(prodId) && !images.isEmpty()) {
                        primaryImageMap.put(prodId, images.get(0).getUrl());
                    }
                });

        Page<ProductAdminListItemResponse> responsePage = page.map(product -> {
            ProductVariantStatsProjection stats = statsMap.get(product.getId());

            long minPrice = product.getBasePrice().longValue();
            long maxPrice = product.getBasePrice().longValue();
            int totalStock = 0;

            if (stats != null) {
                if (stats.getMinPrice() != null) {
                    minPrice = stats.getMinPrice().longValue();
                }
                if (stats.getMaxPrice() != null) {
                    maxPrice = stats.getMaxPrice().longValue();
                }
                if (stats.getTotalStock() != null) {
                    totalStock = stats.getTotalStock().intValue();
                }
            }

            return ProductAdminListItemResponse.builder()
                    .id(product.getId())
                    .name(product.getName())
                    .slug(product.getSlug())
                    .categoryId(product.getCategory().getId())
                    .categoryName(product.getCategory().getName())
                    .basePrice(product.getBasePrice().longValue())
                    .minPrice(minPrice)
                    .maxPrice(maxPrice)
                    .totalStock(totalStock)
                    .isActive(product.getIsActive())
                    .primaryImageUrl(primaryImageMap.get(product.getId()))
                    .createdAt(product.getCreatedAt())
                    .build();
        });

        return PageResponse.of(responsePage);
    }

    @Override
    public ProductDetailResponse getProductById(Long id) {
        Product product = productRepository.findWithCategoryById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với id: " + id));

        List<ProductVariantResponse> variants = variantRepository.findByProductId(id).stream()
                .map(v -> mapToVariantResponse(v, product.getBasePrice()))
                .toList();

        List<ProductImageResponse> images = imageRepository.findByProductIdOrderBySortOrderAsc(id).stream()
                .map(this::mapToImageResponse)
                .toList();

        return mapToProductDetailResponse(product, variants, images);
    }

    @Override
    @Transactional
    public ProductDetailResponse createProduct(ProductUpsertRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục với id: " + request.getCategoryId()));

        String slug;
        if (org.springframework.util.StringUtils.hasText(request.getSlug())) {
            slug = SlugUtils.toSlug(request.getSlug());
        } else {
            slug = SlugUtils.toSlug(request.getName());
        }
        if (!StringUtils.hasText(slug)) {
            slug = "san-pham";
        }

        // Chosen strategy: Return 409 ConflictException when a slug already exists.
        // This allows admin users to explicitly control product SEO slugs rather than generating auto-numbered suffixes.
        if (productRepository.existsBySlug(slug)) {
            throw new ConflictException("Slug '" + slug + "' đã tồn tại, vui lòng chọn tên sản phẩm khác.");
        }

        validateVariantsInRequest(request.getVariants(), null);

        Product product = Product.builder()
                .category(category)
                .name(request.getName().trim())
                .slug(slug)
                .description(request.getDescription())
                .basePrice(BigDecimal.valueOf(request.getBasePrice()))
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .dataSource(DataSourceType.WEBSITE)
                .build();

        Product savedProduct = productRepository.save(product);

        List<ProductVariant> variantsToSave = request.getVariants().stream()
                .map(vReq -> ProductVariant.builder()
                        .product(savedProduct)
                        .sku(vReq.getSku().trim())
                        .size(StringUtils.hasText(vReq.getSize()) ? vReq.getSize().trim() : "N/A")
                        .color(vReq.getColor().trim())
                        .stock(vReq.getStock())
                        .price(vReq.getPrice() != null ? BigDecimal.valueOf(vReq.getPrice()) : null)
                        .build())
                .toList();

        variantRepository.saveAll(variantsToSave);

        return getProductById(savedProduct.getId());
    }

    @Override
    @Transactional
    public ProductDetailResponse updateProduct(Long id, ProductUpsertRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với id: " + id));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục với id: " + request.getCategoryId()));

        // PUT: only update slug if explicitly provided in request; otherwise keep existing slug.
        // This prevents auto-slug recalculation from breaking SEO URLs on name changes.
        if (StringUtils.hasText(request.getSlug())) {
            String newSlug = SlugUtils.toSlug(request.getSlug());
            if (!StringUtils.hasText(newSlug)) {
                newSlug = "san-pham";
            }
            if (productRepository.existsBySlugAndIdNot(newSlug, id)) {
                throw new ConflictException("Slug '" + newSlug + "' đã tồn tại, vui lòng chọn slug khác.");
            }
            product.setSlug(newSlug);
        }

        List<ProductVariant> currentVariants = variantRepository.findByProductId(id);
        Map<Long, ProductVariant> currentVariantMap = currentVariants.stream()
                .collect(Collectors.toMap(ProductVariant::getId, v -> v));

        for (VariantUpsertRequest vReq : request.getVariants()) {
            if (vReq.getId() != null && !currentVariantMap.containsKey(vReq.getId())) {
                throw new BadRequestException("Biến thể với id " + vReq.getId() + " không thuộc sản phẩm này");
            }
        }

        Set<Long> requestVariantIds = request.getVariants().stream()
                .map(VariantUpsertRequest::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<ProductVariant> variantsToDelete = currentVariants.stream()
                .filter(v -> !requestVariantIds.contains(v.getId()))
                .toList();

        for (ProductVariant variantToDelete : variantsToDelete) {
            if (variantToDelete.getStock() != null && variantToDelete.getStock() > 0) {
                throw new ConflictException("Không thể xóa biến thể SKU '" + variantToDelete.getSku()
                        + "' vì còn " + variantToDelete.getStock()
                        + " sản phẩm tồn kho. Hãy điều chỉnh tồn kho về 0 tại trang Kho hàng trước khi xóa.");
            }
            if (variantRepository.isVariantUsedInOrders(variantToDelete.getId())) {
                throw new ConflictException("Không thể xóa biến thể SKU '" + variantToDelete.getSku()
                        + "' vì đã có trong đơn hàng. Vui lòng cập nhật tồn kho về 0 thay vì xóa.");
            }
            if (variantRepository.isVariantUsedInCarts(variantToDelete.getId())) {
                throw new ConflictException("Không thể xóa biến thể SKU '" + variantToDelete.getSku()
                        + "' vì đang có trong giỏ hàng của người dùng. Vui lòng cập nhật tồn kho về 0 thay vì xóa.");
            }
        }

        if (!variantsToDelete.isEmpty()) {
            variantRepository.deleteAll(variantsToDelete);
            variantRepository.flush();
        }

        validateVariantsInRequest(request.getVariants(), id);

        product.setCategory(category);
        product.setName(request.getName().trim());
        product.setDescription(request.getDescription());
        product.setBasePrice(BigDecimal.valueOf(request.getBasePrice()));
        if (request.getIsActive() != null) {
            product.setIsActive(request.getIsActive());
        }

        for (VariantUpsertRequest vReq : request.getVariants()) {
            if (vReq.getId() != null) {
                ProductVariant existingVariant = currentVariantMap.get(vReq.getId());

                existingVariant.setSku(vReq.getSku().trim());
                existingVariant.setSize(StringUtils.hasText(vReq.getSize()) ? vReq.getSize().trim() : "N/A");
                existingVariant.setColor(vReq.getColor().trim());
                existingVariant.setPrice(vReq.getPrice() != null ? BigDecimal.valueOf(vReq.getPrice()) : null);
                variantRepository.save(existingVariant);
            } else {
                ProductVariant newVariant = ProductVariant.builder()
                        .product(product)
                        .sku(vReq.getSku().trim())
                        .size(StringUtils.hasText(vReq.getSize()) ? vReq.getSize().trim() : "N/A")
                        .color(vReq.getColor().trim())
                        .stock(vReq.getStock())
                        .price(vReq.getPrice() != null ? BigDecimal.valueOf(vReq.getPrice()) : null)
                        .build();
                variantRepository.save(newVariant);
            }
        }

        productRepository.save(product);

        return getProductById(id);
    }

    @Override
    @Transactional
    public void updateBulkStatus(BulkStatusRequest request) {
        List<Product> products = productRepository.findAllById(request.getIds());
        for (Product product : products) {
            product.setIsActive(request.getIsActive());
        }
        productRepository.saveAll(products);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với id: " + id));

        if (variantRepository.isProductUsedInOrders(id)) {
            throw new ConflictException("Không thể xóa sản phẩm '" + product.getName()
                    + "' vì đã có biến thể phát sinh trong đơn hàng. Bạn nên chuyển trạng thái sản phẩm sang 'Ngừng bán' (is_active = false).");
        }

        if (productRepository.isProductUsedInReviews(id)) {
            throw new ConflictException("Không thể xóa sản phẩm '" + product.getName()
                    + "' vì đã có đánh giá từ khách hàng. Bạn nên chuyển trạng thái sản phẩm sang 'Ngừng bán' (is_active = false).");
        }

        if (variantRepository.isProductUsedInCarts(id)) {
            throw new ConflictException("Không thể xóa sản phẩm '" + product.getName()
                    + "' vì có biến thể đang nằm trong giỏ hàng của khách hàng. Bạn nên chuyển trạng thái sản phẩm sang 'Ngừng bán' (is_active = false).");
        }

        productRepository.deleteProductPromotionsByProductId(id);
        productRepository.deleteWishlistsByProductId(id);

        List<ProductImage> images = imageRepository.findByProductIdOrderBySortOrderAsc(id);
        for (ProductImage image : images) {
            deletePhysicalFileAfterCommit(image.getUrl());
        }

        imageRepository.deleteByProductId(id);
        variantRepository.deleteAll(variantRepository.findByProductId(id));
        productRepository.delete(product);
    }

    @Override
    @Transactional
    public ProductImageResponse uploadProductImage(
            Long productId,
            MultipartFile file,
            Boolean isPrimary,
            String color,
            String alt
    ) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với id: " + productId));

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File ảnh tải lên không được để trống");
        }

        long maxFileSizeBytes = 10 * 1024 * 1024; // 10 MB
        if (file.getSize() > maxFileSizeBytes) {
            throw new BadRequestException("Kích thước file vượt quá giới hạn cho phép (tối đa 10 MB)");
        }

        String extension = detectImageExtension(file);
        String storedFileName = savePhysicalFile(file, productId, extension);
        String fileUrl = "/uploads/products/" + storedFileName;

        // Cleanup: delete physical file if transaction rolls back after savePhysicalFile succeeds
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            final String fileNameForCleanup = storedFileName;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                        performPhysicalFileDelete(fileNameForCleanup);
                    }
                }
            });
        }

        boolean primary = Boolean.TRUE.equals(isPrimary);
        if (primary) {
            imageRepository.resetPrimaryByProductId(productId);
        } else {
            List<ProductImage> currentImages = imageRepository.findByProductIdOrderBySortOrderAsc(productId);
            if (currentImages.isEmpty()) {
                primary = true;
            }
        }

        List<ProductImage> currentImages = imageRepository.findByProductIdOrderBySortOrderAsc(productId);
        int nextSortOrder = currentImages.size();

        ProductImage image = ProductImage.builder()
                .product(product)
                .url(fileUrl)
                .alt(StringUtils.hasText(alt) ? alt.trim() : product.getName())
                .isPrimary(primary)
                .sortOrder(nextSortOrder)
                .color(StringUtils.hasText(color) ? color.trim() : null)
                .build();

        ProductImage savedImage = imageRepository.save(image);
        return mapToImageResponse(savedImage);
    }

    @Override
    @Transactional
    public ProductImageResponse updateImageMetadata(
            Long productId,
            Long imageId,
            ImageMetadataUpdateRequest request
    ) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Không tìm thấy sản phẩm với id: " + productId);
        }

        ProductImage image = imageRepository.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ảnh với id: " + imageId + " thuộc sản phẩm: " + productId));

        if (request.getAlt() != null) {
            image.setAlt(request.getAlt().trim());
        }

        if (Boolean.TRUE.equals(request.getIsPrimary())) {
            imageRepository.resetPrimaryByProductIdExcept(productId, imageId);
            image.setIsPrimary(true);
        } else if (Boolean.FALSE.equals(request.getIsPrimary())) {
            image.setIsPrimary(false);
        }

        if (request.getSortOrder() != null) {
            image.setSortOrder(request.getSortOrder());
        }

        if (request.getColor() != null) {
            image.setColor(StringUtils.hasText(request.getColor()) ? request.getColor().trim() : null);
        }

        ProductImage updatedImage = imageRepository.save(image);
        return mapToImageResponse(updatedImage);
    }

    @Override
    @Transactional
    public void deleteProductImage(Long productId, Long imageId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Không tìm thấy sản phẩm với id: " + productId);
        }

        ProductImage image = imageRepository.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ảnh với id: " + imageId + " thuộc sản phẩm: " + productId));

        boolean wasPrimary = Boolean.TRUE.equals(image.getIsPrimary());
        deletePhysicalFileAfterCommit(image.getUrl());
        imageRepository.delete(image);

        if (wasPrimary) {
            List<ProductImage> remainingImages = imageRepository.findByProductIdOrderBySortOrderAsc(productId);
            remainingImages.stream()
                    .filter(img -> !img.getId().equals(imageId))
                    .min(Comparator.comparing(ProductImage::getSortOrder).thenComparing(ProductImage::getId))
                    .ifPresent(newPrimary -> {
                        newPrimary.setIsPrimary(true);
                        imageRepository.save(newPrimary);
                    });
        }
    }

    private void validateVariantsInRequest(List<VariantUpsertRequest> variants, Long productId) {
        Set<String> skuSet = new HashSet<>();
        Set<String> sizeColorSet = new HashSet<>();

        for (VariantUpsertRequest vReq : variants) {
            String sku = vReq.getSku().trim();
            if (!skuSet.add(sku.toLowerCase())) {
                throw new BadRequestException("Mã SKU '" + sku + "' bị trùng lặp trong danh sách gửi lên");
            }

            String size = StringUtils.hasText(vReq.getSize()) ? vReq.getSize().trim() : "N/A";
            String color = vReq.getColor().trim();
            String sizeColorKey = size.toLowerCase() + "___" + color.toLowerCase();
            if (!sizeColorSet.add(sizeColorKey)) {
                throw new BadRequestException("Biến thể với kích cỡ '" + size + "' và màu sắc '" + color + "' bị trùng lặp trong danh sách gửi lên");
            }

            if (vReq.getId() != null) {
                if (variantRepository.existsBySkuAndIdNot(sku, vReq.getId())) {
                    throw new ConflictException("Mã SKU '" + sku + "' đã tồn tại trong hệ thống");
                }
                if (productId != null && variantRepository.existsByProductIdAndSizeAndColorAndIdNot(productId, size, color, vReq.getId())) {
                    throw new ConflictException("Biến thể với kích cỡ '" + size + "' và màu sắc '" + color + "' đã tồn tại");
                }
            } else {
                if (vReq.getStock() == null) {
                    throw new BadRequestException("Tồn kho ban đầu không được để trống khi tạo biến thể mới");
                }
                if (variantRepository.existsBySku(sku)) {
                    throw new ConflictException("Mã SKU '" + sku + "' đã tồn tại trong hệ thống");
                }
                if (productId != null && variantRepository.existsByProductIdAndSizeAndColor(productId, size, color)) {
                    throw new ConflictException("Biến thể với kích cỡ '" + size + "' và màu sắc '" + color + "' đã tồn tại");
                }
            }
        }
    }

    private String detectImageExtension(MultipartFile file) {
        try {
            byte[] header = new byte[12];
            try (InputStream is = file.getInputStream()) {
                int bytesRead = is.read(header);
                if (bytesRead < 12) {
                    throw new BadRequestException("File ảnh không hợp lệ hoặc kích thước quá nhỏ");
                }
            }

            // JPEG magic bytes: FF D8 FF
            if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
                return ".jpg";
            }

            // PNG magic bytes: 89 50 4E 47 0D 0A 1A 0A
            if ((header[0] & 0xFF) == 0x89 && (header[1] & 0xFF) == 0x50 && (header[2] & 0xFF) == 0x4E && (header[3] & 0xFF) == 0x47
                    && (header[4] & 0xFF) == 0x0D && (header[5] & 0xFF) == 0x0A && (header[6] & 0xFF) == 0x1A && (header[7] & 0xFF) == 0x0A) {
                return ".png";
            }

            // GIF magic bytes: GIF87a / GIF89a (47 49 46 38)
            if (header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8') {
                return ".gif";
            }

            // WEBP magic bytes: RIFF....WEBP
            if (header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
                return ".webp";
            }

            throw new BadRequestException("Chỉ chấp nhận các định dạng ảnh hợp lệ: JPG, PNG, WEBP, GIF");
        } catch (IOException e) {
            throw new BadRequestException("Không thể đọc nội dung file ảnh");
        }
    }

    private String savePhysicalFile(MultipartFile file, Long productId, String extension) {
        try {
            if (!Files.exists(this.resolvedUploadPath)) {
                Files.createDirectories(this.resolvedUploadPath);
            }

            String storedFileName = "p" + productId + "_" + UUID.randomUUID() + extension;
            Path destination = this.resolvedUploadPath.resolve(storedFileName).normalize();
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

            return storedFileName;
        } catch (IOException e) {
            throw new RuntimeException("Không thể lưu file ảnh vào thư mục máy chủ", e);
        }
    }

    private void deletePhysicalFileAfterCommit(String url) {
        if (!StringUtils.hasText(url) || !url.startsWith("/uploads/products/")) {
            return;
        }
        String fileName = url.substring("/uploads/products/".length());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    performPhysicalFileDelete(fileName);
                }
            });
        } else {
            performPhysicalFileDelete(fileName);
        }
    }

    private void performPhysicalFileDelete(String fileName) {
        try {
            Path filePath = this.resolvedUploadPath.resolve(fileName).normalize();
            Files.deleteIfExists(filePath);
        } catch (Exception e) {
            log.warn("Failed to delete physical file: {}", fileName, e);
        }
    }

    private ProductVariantResponse mapToVariantResponse(ProductVariant variant, BigDecimal productBasePrice) {
        Long price = variant.getPrice() != null ? variant.getPrice().longValue() : null;
        long effectivePrice = price != null ? price : productBasePrice.longValue();

        return ProductVariantResponse.builder()
                .id(variant.getId())
                .sku(variant.getSku())
                .size(variant.getSize())
                .color(variant.getColor())
                .stock(variant.getStock())
                .price(price)
                .effectivePrice(effectivePrice)
                .sourceArticleId(variant.getSourceArticleId())
                .build();
    }

    private ProductImageResponse mapToImageResponse(ProductImage image) {
        return ProductImageResponse.builder()
                .id(image.getId())
                .url(image.getUrl())
                .alt(image.getAlt())
                .isPrimary(image.getIsPrimary())
                .sortOrder(image.getSortOrder())
                .color(image.getColor())
                .build();
    }

    private ProductDetailResponse mapToProductDetailResponse(
            Product product,
            List<ProductVariantResponse> variants,
            List<ProductImageResponse> images
    ) {
        return ProductDetailResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .basePrice(product.getBasePrice().longValue())
                .isActive(product.getIsActive())
                .dataSource(product.getDataSource() != null ? product.getDataSource().name().toLowerCase() : null)
                .sourceProductCode(product.getSourceProductCode())
                .createdAt(product.getCreatedAt())
                .variants(variants)
                .images(images)
                .build();
    }

    @Override
    public PageResponse<LowStockVariantResponse> getLowStockVariants(Integer threshold, Integer page, Integer size) {
        int safeThreshold = (threshold != null) ? threshold : (storeSettingsReader != null ? storeSettingsReader.getLowStockThreshold() : 10);
        if (safeThreshold < 0) {
            throw new BadRequestException("Ngưỡng tồn kho không được là số âm");
        }
        int pageNumber = (page != null) ? Math.max(0, page) : 0;
        int pageSize = (size != null) ? size : 10;
        if (pageSize < 1) {
            throw new BadRequestException("Số lượng bản ghi (size) phải lớn hơn hoặc bằng 1");
        }
        pageSize = Math.min(pageSize, 100);

        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        Page<ProductVariant> variantsPage = variantRepository.findLowStockVariants(safeThreshold, pageable);

        List<LowStockVariantResponse> content = variantsPage.getContent().stream()
                .map(this::toLowStockVariantResponse)
                .toList();

        return PageResponse.<LowStockVariantResponse>builder()
                .content(content)
                .page(variantsPage.getNumber())
                .size(variantsPage.getSize())
                .totalElements(variantsPage.getTotalElements())
                .totalPages(variantsPage.getTotalPages())
                .build();
    }

    @Override
    public long countLowStockVariants(Integer threshold) {
        int safeThreshold = (threshold != null) ? threshold : (storeSettingsReader != null ? storeSettingsReader.getLowStockThreshold() : 10);
        if (safeThreshold < 0) {
            throw new BadRequestException("Ngưỡng tồn kho không được là số âm");
        }
        return variantRepository.countLowStockVariants(safeThreshold);
    }

    private LowStockVariantResponse toLowStockVariantResponse(ProductVariant variant) {
        return LowStockVariantResponse.builder()
                .id(variant.getId())
                .productId(variant.getProduct().getId())
                .productName(variant.getProduct().getName())
                .sku(variant.getSku())
                .size(variant.getSize())
                .color(variant.getColor())
                .stock(variant.getStock())
                .build();
    }
}
