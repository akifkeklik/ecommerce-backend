package com.ecommerce.domain.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/** One "helpful" vote per reader per review; the primary key enforces it. */
@Entity
@Table(name = "review_helpful_votes")
@IdClass(ReviewHelpfulVote.Key.class)
public class ReviewHelpfulVote {

    @Id
    @Column(name = "review_id", nullable = false)
    private UUID reviewId;

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected ReviewHelpfulVote() {
    }

    public ReviewHelpfulVote(UUID reviewId, UUID userId) {
        this.reviewId = reviewId;
        this.userId = userId;
    }

    public UUID getReviewId() {
        return reviewId;
    }

    public UUID getUserId() {
        return userId;
    }

    public static class Key implements Serializable {
        private UUID reviewId;
        private UUID userId;

        public Key() {
        }

        public Key(UUID reviewId, UUID userId) {
            this.reviewId = reviewId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(reviewId, k.reviewId) && Objects.equals(userId, k.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(reviewId, userId);
        }
    }
}
