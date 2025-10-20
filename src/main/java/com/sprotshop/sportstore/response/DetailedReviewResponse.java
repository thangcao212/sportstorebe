package com.sprotshop.sportstore.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class DetailedReviewResponse {
    private ReviewResponse review;
    private List<ReviewReplyResponse> replies;
}