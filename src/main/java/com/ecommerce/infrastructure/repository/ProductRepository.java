package com.ecommerce.infrastructure.repository;

import com.ecommerce.domain.product.Product;
import com.ecommerce.domain.product.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Product entity operations.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    Optional<Product> findBySku(String sku);

    Optional<Product> findBySlug(String slug);

    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.status = 'ACTIVE' AND p.deleted = false")
    Optional<Product> findActiveById(@Param("id") UUID id);

    /** Locks the product row while an order reserves stock to prevent overselling. */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.status = 'ACTIVE' AND p.deleted = false")
    Optional<Product> findActiveByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT p FROM Product p WHERE p.slug = :slug AND p.status = 'ACTIVE' AND p.deleted = false")
    Optional<Product> findActiveBySlug(@Param("slug") String slug);

    boolean existsBySku(String sku);

    boolean existsBySlug(String slug);

    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    Page<Product> findByCategoryId(UUID categoryId, Pageable pageable);

    Page<Product> findBySellerId(UUID sellerId, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.status = :status AND p.deleted = false")
    Page<Product> findActiveProducts(@Param("status") ProductStatus status, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.category.id = :categoryId AND p.status = 'ACTIVE' AND p.deleted = false")
    Page<Product> findActiveByCategory(@Param("categoryId") UUID categoryId, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.featured = true AND p.status = 'ACTIVE' AND p.deleted = false")
    List<Product> findFeaturedProducts();

    @Query("SELECT p FROM Product p WHERE p.stockQuantity <= p.lowStockThreshold AND p.deleted = false")
    List<Product> findLowStockProducts();

    @Query("SELECT p FROM Product p WHERE p.status = 'ACTIVE' AND p.deleted = false AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Product> searchProducts(@Param("query") String query, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.status = 'ACTIVE' AND p.deleted = false AND p.price BETWEEN :minPrice AND :maxPrice")
    Page<Product> findByPriceRange(@Param("minPrice") BigDecimal minPrice, @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.status = 'ACTIVE' AND p.deleted = false ORDER BY p.totalSales DESC")
    List<Product> findBestSellers(Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.status = 'ACTIVE' AND p.deleted = false ORDER BY p.createdAt DESC")
    List<Product> findNewArrivals(Pageable pageable);
}
