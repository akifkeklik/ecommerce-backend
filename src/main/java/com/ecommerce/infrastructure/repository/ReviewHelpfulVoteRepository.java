package com.ecommerce.infrastructure.repository;

import com.ecommerce.domain.review.ReviewHelpfulVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewHelpfulVoteRepository extends JpaRepository<ReviewHelpfulVote, ReviewHelpfulVote.Key> {
}
