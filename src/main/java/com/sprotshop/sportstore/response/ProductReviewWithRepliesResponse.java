package com.sprotshop.sportstore.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductReviewWithRepliesResponse {
    private ReviewResponse review;
    private List<ReviewReplyResponse> replies;  // Phản hồi của admin
}