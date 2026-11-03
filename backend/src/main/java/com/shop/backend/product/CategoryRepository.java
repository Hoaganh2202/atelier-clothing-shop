package com.shop.backend.product;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    boolean existsBySlugIgnoreCase(String slug);

    boolean existsBySlugIgnoreCaseAndIdNot(String slug, Long id);

    boolean existsByParent_Id(Long parentId);

    List<Category> findByIsActiveTrue();

    Optional<Category> findByIdAndIsActiveTrue(Long id);

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.parent ORDER BY c.id ASC")
    List<Category> findAllWithParent();
}
