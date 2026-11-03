package com.shop.backend.product;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderBySortOrderAsc(Long productId);

    List<ProductImage> findByProductIdIn(Collection<Long> productIds);

    Optional<ProductImage> findByIdAndProductId(Long id, Long productId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ProductImage pi SET pi.isPrimary = false WHERE pi.product.id = :productId")
    void resetPrimaryByProductId(@Param("productId") Long productId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ProductImage pi SET pi.isPrimary = false WHERE pi.product.id = :productId AND pi.id != :excludedImageId")
    void resetPrimaryByProductIdExcept(@Param("productId") Long productId, @Param("excludedImageId") Long excludedImageId);

    void deleteByProductId(Long productId);
}
