// File: com/sprotshop/sportstore/service/ReviewService.java
package com.sprotshop.sportstore.service;

import com.sprotshop.sportstore.Enum.ReviewStatus;
import com.sprotshop.sportstore.request.ReviewRequest;
import com.sprotshop.sportstore.response.*;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface ReviewService {
    ReviewResponse createReview(ReviewRequest request, List<MultipartFile> images);
    ReviewResponse updateReview(Long reviewId, ReviewRequest request, List<MultipartFile> images);
    void deleteReview(Long reviewId);
    PageResponse<ProductReviewWithRepliesResponse> getReviewsByProduct(Long productId, Pageable pageable);
    PageResponse<ReviewResponse> getReviewsByCurrentUser(Pageable pageable);
    ReviewResponse getReviewById(Long reviewId);
    PageResponse<ReviewResponse> getAllReviews(Pageable pageable, String search, String status, Long productId);

    // New admin methods
    ReviewResponse updateReviewStatus(Long reviewId, ReviewStatus status);
    ReviewResponse replyToReview(Long reviewId, String replyContent);
    DetailedReviewResponse getDetailedReview(Long reviewId);
    PageResponse<ReviewResponse> getAllReviews(Pageable pageable, String search, String status, Long productId, Integer minRating, Integer maxRating, String startDate, String endDate);
    ReviewStatsResponse getReviewStats();
}