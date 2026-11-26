package com.ecommerce.infrastructure.repository;

import com.ecommerce.domain.review.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Review entity operations.
 */
@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Page<Review> findByProductId(UUID productId, Pageable pageable);

    Page<Review> findByUserId(UUID userId, Pageable pageable);

    // The author is loaded with the review: responses are built after the transaction (open-in-view is off).
    @EntityGraph(attributePaths = "user")
    Page<Review> findByProductIdAndApproved(UUID productId, boolean approved, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Optional<Review> findWithUserById(UUID id);

    Optional<Review> findByProductIdAndUserId(UUID productId, UUID userId);

    boolean existsByProductIdAndUserId(UUID productId, UUID userId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.product.id = :productId AND r.approved = true")
    Double getAverageRatingByProductId(@Param("productId") UUID productId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.product.id = :productId AND r.approved = true")
    Long countApprovedByProductId(@Param("productId") UUID productId);

    @EntityGraph(attributePaths = "user")
    @Query("SELECT r FROM Review r WHERE r.approved = false AND r.deleted = false ORDER BY r.createdAt ASC")
    Page<Review> findPendingReviews(Pageable pageable);
}
