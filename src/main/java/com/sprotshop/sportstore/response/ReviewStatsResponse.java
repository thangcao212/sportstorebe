package com.sprotshop.sportstore.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ReviewStatsResponse {
    private Long totalReviews;
    private Long pendingReviews;
    private Long approvedReviews;
    private Long hiddenReviews;
    private List<ProductReviewCount> topProducts;
    private Double averageRating;
    private SentimentStats sentimentStats;
}