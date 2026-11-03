package com.shop.backend.inventory;

import com.shop.backend.inventory.dto.InventorySummaryAggregate;
import com.shop.backend.inventory.dto.InventoryVariantProjection;
import com.shop.backend.product.ProductVariant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryVariantRepository extends JpaRepository<ProductVariant, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ProductVariant pv SET pv.stock = :newStock WHERE pv.id = :id AND pv.stock = :expectedStock")
    int compareAndSwapStock(
            @Param("id") Long id,
            @Param("newStock") Integer newStock,
            @Param("expectedStock") Integer expectedStock
    );

    @Modifying(flushAutomatically = true)
    @Query("UPDATE ProductVariant pv SET pv.stock = pv.stock + :quantity WHERE pv.id = :id")
    int restoreStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    @Query("SELECT pv.stock FROM ProductVariant pv WHERE pv.id = :id")
    Integer findStockById(@Param("id") Long id);

    @Query("SELECT new com.shop.backend.inventory.dto.InventorySummaryAggregate(" +
           "COUNT(pv.id), " +
           "COALESCE(SUM(pv.stock), 0L), " +
           "COALESCE(SUM(CASE WHEN pv.stock = 0 THEN 1L ELSE 0L END), 0L), " +
           "COALESCE(SUM(CASE WHEN pv.stock > 0 AND pv.stock < :threshold THEN 1L ELSE 0L END), 0L), " +
           "COALESCE(SUM(CASE WHEN pv.stock >= :threshold THEN 1L ELSE 0L END), 0L)" +
           ") FROM ProductVariant pv")
    InventorySummaryAggregate getInventorySummary(@Param("threshold") int threshold);

    @Query(value = "SELECT new com.shop.backend.inventory.dto.InventoryVariantProjection(" +
           "pv.id, " +
           "p.id, " +
           "p.name, " +
           "p.isActive, " +
           "(SELECT MAX(pi.url) FROM ProductImage pi WHERE pi.product.id = p.id AND pi.isPrimary = true), " +
           "c.name, " +
           "pv.sku, " +
           "pv.size, " +
           "pv.color, " +
           "pv.stock, " +
           "COALESCE(pv.price, p.basePrice)" +
           ") " +
           "FROM ProductVariant pv " +
           "JOIN pv.product p " +
           "LEFT JOIN p.category c " +
           "WHERE (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(pv.sku) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:categoryId IS NULL OR c.id = :categoryId) " +
           "AND (:size IS NULL OR pv.size = :size) " +
           "AND (:color IS NULL OR pv.color = :color) " +
           "AND (:productActive IS NULL OR p.isActive = :productActive) " +
           "AND (:statusFilter = 'ALL' " +
           "     OR (:statusFilter = 'OUT_OF_STOCK' AND pv.stock = 0) " +
           "     OR (:statusFilter = 'LOW_STOCK' AND pv.stock > 0 AND pv.stock < :threshold) " +
           "     OR (:statusFilter = 'IN_STOCK' AND pv.stock >= :threshold))",
           countQuery = "SELECT COUNT(pv) FROM ProductVariant pv " +
           "JOIN pv.product p " +
           "LEFT JOIN p.category c " +
           "WHERE (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(pv.sku) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:categoryId IS NULL OR c.id = :categoryId) " +
           "AND (:size IS NULL OR pv.size = :size) " +
           "AND (:color IS NULL OR pv.color = :color) " +
           "AND (:productActive IS NULL OR p.isActive = :productActive) " +
           "AND (:statusFilter = 'ALL' " +
           "     OR (:statusFilter = 'OUT_OF_STOCK' AND pv.stock = 0) " +
           "     OR (:statusFilter = 'LOW_STOCK' AND pv.stock > 0 AND pv.stock < :threshold) " +
           "     OR (:statusFilter = 'IN_STOCK' AND pv.stock >= :threshold))")
    Page<InventoryVariantProjection> findVariantsByFilter(
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("size") String size,
            @Param("color") String color,
            @Param("statusFilter") String statusFilter,
            @Param("threshold") int threshold,
            @Param("productActive") Boolean productActive,
            Pageable pageable
    );

    @Query("SELECT new com.shop.backend.inventory.dto.InventoryVariantProjection(" +
           "pv.id, " +
           "p.id, " +
           "p.name, " +
           "p.isActive, " +
           "(SELECT MAX(pi.url) FROM ProductImage pi WHERE pi.product.id = p.id AND pi.isPrimary = true), " +
           "c.name, " +
           "pv.sku, " +
           "pv.size, " +
           "pv.color, " +
           "pv.stock, " +
           "COALESCE(pv.price, p.basePrice)" +
           ") " +
           "FROM ProductVariant pv " +
           "JOIN pv.product p " +
           "LEFT JOIN p.category c " +
           "WHERE pv.id = :id")
    Optional<InventoryVariantProjection> findVariantProjectionById(@Param("id") Long id);
}
