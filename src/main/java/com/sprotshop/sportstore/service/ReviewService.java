
package com.sprotshop.sportstore.service;

import com.sprotshop.sportstore.request.ReviewRequest;
import com.sprotshop.sportstore.response.PageResponse;
import com.sprotshop.sportstore.response.ReviewResponse;
import org.springframework.data.domain.Pageable;

public interface ReviewService {
    ReviewResponse createReview(ReviewRequest request);
    ReviewResponse updateReview(Long reviewId, ReviewRequest request);
    void deleteReview(Long reviewId);
    PageResponse<ReviewResponse> getReviewsByProduct(Long productId, Pageable pageable);
    PageResponse<ReviewResponse> getReviewsByCurrentUser(Pageable pageable);  // Changed to current user
    ReviewResponse getReviewById(Long reviewId);
}