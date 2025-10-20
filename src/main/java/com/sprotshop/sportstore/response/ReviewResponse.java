// File: com/sprotshop/sportstore/response/ReviewResponse.java
package com.sprotshop.sportstore.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sprotshop.sportstore.Enum.ReviewStatus;
import com.sprotshop.sportstore.entity.Review;
import com.sprotshop.sportstore.entity.ReviewImage;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {
    private Long id;
    private Long userId;
    private String userName;
    private Long productId;
    private String productName;
    private Integer rating;
    private String comment;
    private List<String> imageUrls;
    private ReviewStatus status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    public static ReviewResponse fromEntity(Review review) {
        List<String> imageUrls = review.getImages().stream()
                .map(ReviewImage::getImageUrl)
                .collect(Collectors.toList());
        return ReviewResponse.builder()
                .id(review.getId())
                .userId(review.getUser().getId())
                .userName(review.getUser().getUsername())
                .productId(review.getProduct().getId())
                .productName(review.getProduct().getName())
                .rating(review.getRating())
                .comment(review.getComment())
                .imageUrls(imageUrls)
                .status(review.getStatus())
                .createdAt(review.getCreatedAt())
                .build();
    }
}