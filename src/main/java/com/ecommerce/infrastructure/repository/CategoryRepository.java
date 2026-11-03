package com.ecommerce.infrastructure.repository;

import com.ecommerce.domain.product.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Category entity operations.
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    @Query("SELECT c FROM Category c WHERE c.slug = :slug AND c.active = true AND c.deleted = false")
    Optional<Category> findBySlug(@Param("slug") String slug);

    boolean existsBySlug(String slug);

    List<Category> findByParentIsNull();

    List<Category> findByParentId(UUID parentId);

    @Query("SELECT c FROM Category c WHERE c.active = true AND c.deleted = false ORDER BY c.displayOrder")
    List<Category> findAllActive();

    @Query("SELECT c FROM Category c WHERE c.parent IS NULL AND c.active = true AND c.deleted = false ORDER BY c.displayOrder")
    List<Category> findActiveRootCategories();

    @Query("SELECT c FROM Category c WHERE c.featured = true AND c.active = true AND c.deleted = false")
    List<Category> findFeaturedCategories();
}
