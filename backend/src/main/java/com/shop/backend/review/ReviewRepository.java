package com.shop.backend.review;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @EntityGraph(attributePaths = {"user"})
    Page<Review> findByProductId(Long productId, Pageable pageable);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.product.id = :productId GROUP BY r.rating")
    List<Object[]> countByRatingGrouped(@Param("productId") Long productId);

    @Query(value = """
            SELECT COUNT(*)
            FROM order_details od
            JOIN product_variants pv ON pv.id = od.variant_id
            JOIN orders o ON o.id = od.order_id
            WHERE pv.product_id = :productId
              AND o.user_id = :userId
              AND LOWER(o.status) = 'completed'
            """, nativeQuery = true)
    long countCompletedPurchases(@Param("userId") Long userId, @Param("productId") Long productId);
}
