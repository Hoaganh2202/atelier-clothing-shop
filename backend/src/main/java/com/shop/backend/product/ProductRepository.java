package com.shop.backend.product;

import com.shop.backend.product.dto.CategoryProductCountProjection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    boolean existsByCategoryId(Long categoryId);

    @Query("SELECT p.category.id AS categoryId, COUNT(p.id) AS productCount FROM Product p GROUP BY p.category.id")
    List<CategoryProductCountProjection> countProductsGroupedByCategoryId();

    @Override
    @EntityGraph(attributePaths = { "category" })
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    @Query("SELECT p FROM Product p JOIN FETCH p.category WHERE p.id = :id")
    Optional<Product> findWithCategoryById(@Param("id") Long id);

    @Query("SELECT p FROM Product p JOIN FETCH p.category c LEFT JOIN FETCH c.parent WHERE p.slug = :slug")
    Optional<Product> findWithCategoryBySlug(@Param("slug") String slug);

    @EntityGraph(attributePaths = { "category" })
    List<Product> findTop8ByCategoryIdAndIdNotAndIsActiveTrueOrderByCreatedAtDescIdDesc(Long categoryId, Long id);

    // TODO: Delegate to ReviewService when review module is implemented
    @Query(value = "SELECT COUNT(*) FROM reviews WHERE product_id = :productId", nativeQuery = true)
    long countProductInReviews(@Param("productId") Long productId);

    default boolean isProductUsedInReviews(Long productId) {
        return countProductInReviews(productId) > 0;
    }

    // TODO: Delegate to PromotionService when promotion module is implemented
    @Modifying
    @Query(value = "DELETE FROM product_promotions WHERE product_id = :productId", nativeQuery = true)
    void deleteProductPromotionsByProductId(@Param("productId") Long productId);

    // TODO: Delegate to WishlistService when wishlist module is implemented
    @Modifying
    @Query(value = "DELETE FROM wishlists WHERE product_id = :productId", nativeQuery = true)
    void deleteWishlistsByProductId(@Param("productId") Long productId);
}
