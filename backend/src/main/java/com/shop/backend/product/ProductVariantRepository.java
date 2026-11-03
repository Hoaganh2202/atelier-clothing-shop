package com.shop.backend.product;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    List<ProductVariant> findByProductId(Long productId);

    List<ProductVariant> findByProductIdIn(Collection<Long> productIds);

    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNot(String sku, Long id);

    boolean existsByProductIdAndSizeAndColor(Long productId, String size, String color);

    boolean existsByProductIdAndSizeAndColorAndIdNot(Long productId, String size, String color, Long id);

    @Query(value = "SELECT COUNT(*) FROM order_details WHERE variant_id = :variantId", nativeQuery = true)
    long countVariantInOrders(@Param("variantId") Long variantId);

    default boolean isVariantUsedInOrders(Long variantId) {
        return countVariantInOrders(variantId) > 0;
    }

    @Query(value = "SELECT COUNT(*) FROM cart_items WHERE variant_id = :variantId", nativeQuery = true)
    long countVariantInCarts(@Param("variantId") Long variantId);

    default boolean isVariantUsedInCarts(Long variantId) {
        return countVariantInCarts(variantId) > 0;
    }

    @Query(value = "SELECT COUNT(*) FROM order_details od JOIN product_variants pv ON od.variant_id = pv.id WHERE pv.product_id = :productId", nativeQuery = true)
    long countProductVariantsInOrders(@Param("productId") Long productId);

    default boolean isProductUsedInOrders(Long productId) {
        return countProductVariantsInOrders(productId) > 0;
    }

    @Query(value = "SELECT COUNT(*) FROM cart_items ci JOIN product_variants pv ON ci.variant_id = pv.id WHERE pv.product_id = :productId", nativeQuery = true)
    long countProductVariantsInCarts(@Param("productId") Long productId);

    default boolean isProductUsedInCarts(Long productId) {
        return countProductVariantsInCarts(productId) > 0;
    }

    @Query("SELECT pv.product.id AS productId, " +
           "COALESCE(SUM(pv.stock), 0) AS totalStock, " +
           "MIN(COALESCE(pv.price, pv.product.basePrice)) AS minPrice, " +
           "MAX(COALESCE(pv.price, pv.product.basePrice)) AS maxPrice " +
           "FROM ProductVariant pv " +
           "WHERE pv.product.id IN :productIds " +
           "GROUP BY pv.product.id")
    List<ProductVariantStatsProjection> getVariantStatsByProductIds(@Param("productIds") Collection<Long> productIds);

    @Query("SELECT DISTINCT pv.size FROM ProductVariant pv WHERE pv.product.isActive = true AND pv.product.category.id IN :categoryIds ORDER BY pv.size ASC")
    List<String> findDistinctSizesByCategoryIds(@Param("categoryIds") Collection<Long> categoryIds);

    @Query("SELECT DISTINCT pv.color FROM ProductVariant pv WHERE pv.product.isActive = true AND pv.product.category.id IN :categoryIds ORDER BY pv.color ASC")
    List<String> findDistinctColorsByCategoryIds(@Param("categoryIds") Collection<Long> categoryIds);

    @Query("SELECT MIN(COALESCE(pv.price, pv.product.basePrice)) AS minPrice, " +
           "MAX(COALESCE(pv.price, pv.product.basePrice)) AS maxPrice " +
           "FROM ProductVariant pv WHERE pv.product.isActive = true AND pv.product.category.id IN :categoryIds")
    PriceRangeProjection getPriceRangeByCategoryIds(@Param("categoryIds") Collection<Long> categoryIds);

    @Query("SELECT DISTINCT pv.size FROM ProductVariant pv WHERE pv.product.isActive = true ORDER BY pv.size ASC")
    List<String> findDistinctSizesAllActive();

    @Query("SELECT DISTINCT pv.color FROM ProductVariant pv WHERE pv.product.isActive = true ORDER BY pv.color ASC")
    List<String> findDistinctColorsAllActive();

    @Query("SELECT MIN(COALESCE(pv.price, pv.product.basePrice)) AS minPrice, " +
           "MAX(COALESCE(pv.price, pv.product.basePrice)) AS maxPrice " +
           "FROM ProductVariant pv WHERE pv.product.isActive = true")
    PriceRangeProjection getPriceRangeAllActive();

    @Query(
            value = "SELECT pv FROM ProductVariant pv " +
                    "JOIN FETCH pv.product p " +
                    "WHERE pv.stock < :threshold " +
                    "AND p.isActive = true " +
                    "ORDER BY pv.stock ASC, pv.id ASC",
            countQuery = "SELECT COUNT(pv) FROM ProductVariant pv " +
                         "JOIN pv.product p " +
                         "WHERE pv.stock < :threshold " +
                         "AND p.isActive = true"
    )
    Page<ProductVariant> findLowStockVariants(@Param("threshold") int threshold, Pageable pageable);

    @Query("SELECT COUNT(pv) FROM ProductVariant pv JOIN pv.product p WHERE pv.stock < :threshold AND p.isActive = true")
    long countLowStockVariants(@Param("threshold") int threshold);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ProductVariant pv SET pv.stock = pv.stock - :quantity WHERE pv.id = :id AND pv.stock >= :quantity")
    int deductStock(@Param("id") Long id, @Param("quantity") Integer quantity);
}
