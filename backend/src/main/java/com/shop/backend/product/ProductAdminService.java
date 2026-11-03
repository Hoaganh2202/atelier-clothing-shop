package com.shop.backend.product;

import com.shop.backend.common.dto.PageResponse;
import com.shop.backend.product.dto.BulkStatusRequest;
import com.shop.backend.product.dto.ImageMetadataUpdateRequest;
import com.shop.backend.product.dto.LowStockVariantResponse;
import com.shop.backend.product.dto.ProductAdminListItemResponse;
import com.shop.backend.product.dto.ProductDetailResponse;
import com.shop.backend.product.dto.ProductImageResponse;
import com.shop.backend.product.dto.ProductUpsertRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface ProductAdminService {

    PageResponse<ProductAdminListItemResponse> getProducts(
            String keyword,
            Long categoryId,
            Boolean isActive,
            Pageable pageable
    );

    ProductDetailResponse getProductById(Long id);

    ProductDetailResponse createProduct(ProductUpsertRequest request);

    ProductDetailResponse updateProduct(Long id, ProductUpsertRequest request);

    void updateBulkStatus(BulkStatusRequest request);

    void deleteProduct(Long id);

    ProductImageResponse uploadProductImage(
            Long productId,
            MultipartFile file,
            Boolean isPrimary,
            String color,
            String alt
    );

    ProductImageResponse updateImageMetadata(
            Long productId,
            Long imageId,
            ImageMetadataUpdateRequest request
    );

    void deleteProductImage(Long productId, Long imageId);

    PageResponse<LowStockVariantResponse> getLowStockVariants(Integer threshold, Integer page, Integer size);

    long countLowStockVariants(Integer threshold);
}
