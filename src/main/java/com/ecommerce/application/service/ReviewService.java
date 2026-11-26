package com.ecommerce.application.service;

import com.ecommerce.domain.exception.EntityNotFoundException;
import com.ecommerce.domain.review.Review;
import com.ecommerce.domain.review.ReviewHelpfulVote;
import com.ecommerce.domain.user.User;
import com.ecommerce.infrastructure.repository.OrderRepository;
import com.ecommerce.infrastructure.repository.ProductRepository;
import com.ecommerce.infrastructure.repository.ReviewHelpfulVoteRepository;
import com.ecommerce.infrastructure.repository.ReviewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Reviews are held for moderation, then shown. "Verified purchase" means the reader has a delivered order
 * containing the product, and each reader can mark a review helpful once.
 */
@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final ReviewHelpfulVoteRepository voteRepository;

    public ReviewService(ReviewRepository reviewRepository, ProductRepository productRepository,
            OrderRepository orderRepository, ReviewHelpfulVoteRepository voteRepository) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.voteRepository = voteRepository;
    }

    @Transactional
    public Review create(User author, UUID productId, int rating, String title, String comment) {
        var product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product", productId));
        if (reviewRepository.existsByProductIdAndUserId(productId, author.getId())) {
            throw new IllegalStateException("You have already reviewed this product");
        }

        Review review = new Review();
        review.setProduct(product);
        review.setUser(author);
        review.setRating(rating);
        review.setTitle(title);
        review.setComment(comment);
        review.setVerifiedPurchase(orderRepository.existsDeliveredOrderWithProduct(author.getId(), productId));
        review.setApproved(false);
        return reviewRepository.save(review);
    }

    @Transactional(readOnly = true)
    public Page<Review> pending(Pageable pageable) {
        return reviewRepository.findPendingReviews(pageable);
    }

    @Transactional
    public Review approve(UUID reviewId, User moderator) {
        Review review = find(reviewId);
        review.approve(moderator.getUsername());
        return reviewRepository.save(review);
    }

    @Transactional
    public Review reject(UUID reviewId) {
        Review review = find(reviewId);
        review.reject();
        return reviewRepository.save(review);
    }

    /**
     * Counts a reader's vote once. Votes on hidden reviews and on one's own review are refused.
     * Returns false when the reader had already voted.
     */
    @Transactional
    public boolean markHelpful(UUID reviewId, User voter) {
        Review review = find(reviewId);
        if (!review.isApproved()) {
            throw new EntityNotFoundException("Review", reviewId);
        }
        if (review.getUser().getId().equals(voter.getId())) {
            throw new IllegalArgumentException("You cannot vote on your own review");
        }
        var key = new ReviewHelpfulVote.Key(reviewId, voter.getId());
        if (voteRepository.existsById(key)) {
            return false;
        }
        voteRepository.save(new ReviewHelpfulVote(reviewId, voter.getId()));
        review.markHelpful();
        reviewRepository.save(review);
        return true;
    }

    private Review find(UUID reviewId) {
        return reviewRepository.findWithUserById(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("Review", reviewId));
    }
}
