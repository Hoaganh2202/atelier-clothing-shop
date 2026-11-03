package com.shop.backend.promotion;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PromotionProductRepository extends JpaRepository<PromotionProduct, PromotionProductId> {

    @Query("SELECT pp.id.productId FROM PromotionProduct pp WHERE pp.id.promotionId = :promotionId")
    List<Long> findProductIdsByPromotionId(@Param("promotionId") Long promotionId);

    @Query("SELECT pp.id.promotionId AS promotionId, COUNT(pp.id.productId) AS productCount "
            + "FROM PromotionProduct pp WHERE pp.id.promotionId IN :promotionIds "
            + "GROUP BY pp.id.promotionId")
    List<PromotionProductCountProjection> countProductsByPromotionIds(
            @Param("promotionIds") Collection<Long> promotionIds);

    @Transactional
    @Modifying
    @Query("DELETE FROM PromotionProduct pp WHERE pp.id.promotionId = :promotionId")
    void deleteByPromotionId(@Param("promotionId") Long promotionId);

    @Transactional
    @Modifying
    @Query("DELETE FROM PromotionProduct pp WHERE pp.id.promotionId = :promotionId AND pp.id.productId IN :productIds")
    void deleteByPromotionIdAndProductIdIn(
            @Param("promotionId") Long promotionId,
            @Param("productIds") Collection<Long> productIds);

    @Query("SELECT pp.id.promotionId FROM PromotionProduct pp WHERE pp.id.productId = :productId")
    List<Long> findPromotionIdsByProductId(@Param("productId") Long productId);
}
