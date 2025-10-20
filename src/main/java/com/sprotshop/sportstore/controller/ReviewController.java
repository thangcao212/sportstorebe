package com.sprotshop.sportstore.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprotshop.sportstore.Enum.ReviewStatus;
import com.sprotshop.sportstore.request.ReviewAdminRequest;
import com.sprotshop.sportstore.request.ReviewRequest;
import com.sprotshop.sportstore.response.ApiResponse;
import com.sprotshop.sportstore.response.DetailedReviewResponse;
import com.sprotshop.sportstore.response.PageResponse;
import com.sprotshop.sportstore.response.ReviewResponse;
import com.sprotshop.sportstore.response.ReviewStatsResponse;
import com.sprotshop.sportstore.response.ProductReviewWithRepliesResponse;  // FIXED: Import mới cho replies
import com.sprotshop.sportstore.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;  // FIXED: Thêm log nếu cần debug
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Slf4j  // FIXED: Để log endpoint nếu cần
public class ReviewController {
    private final ReviewService reviewService;

    // User endpoints (require authentication)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ReviewResponse> createReview(
            @RequestPart(value = "review", required = true) String reviewJson,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) throws JsonProcessingException {

        ObjectMapper objectMapper = new ObjectMapper();
        ReviewRequest request = objectMapper.readValue(reviewJson, ReviewRequest.class);

        log.info("Creating review for productId: {}", request.getProductId());
        ReviewResponse response = reviewService.createReview(request, images);
        return ApiResponse.success("Tạo đánh giá thành công", response);
    }


    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ReviewResponse> updateReview(
            @PathVariable Long id,
            @RequestPart("review") String reviewJson,
            @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) throws JsonProcessingException {
        // ✅ Parse JSON thủ công để tránh lỗi Content-Type
        ObjectMapper objectMapper = new ObjectMapper();
        ReviewRequest request = objectMapper.readValue(reviewJson, ReviewRequest.class);

        log.info("Updating reviewId: {}", id);
        ReviewResponse response = reviewService.updateReview(id, request, images);
        return ApiResponse.success("Cập nhật đánh giá thành công", response);
    }


    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteReview(@PathVariable Long id) {
        log.info("Deleting reviewId: {}", id);  // FIXED: Log
        reviewService.deleteReview(id);
        return ApiResponse.success("Xóa đánh giá thành công", null);
    }

    @GetMapping("/product/{productId}")
    public ApiResponse<PageResponse<ProductReviewWithRepliesResponse>> getReviewsByProduct(  // FIXED: Đổi return type để include replies
                                                                                             @PathVariable Long productId,
                                                                                             @RequestParam(defaultValue = "0") int page,
                                                                                             @RequestParam(defaultValue = "10") int size) {
        log.info("Fetching reviews for productId: {} (page={}, size={})", productId, page, size);  // FIXED: Log
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<ProductReviewWithRepliesResponse> response = reviewService.getReviewsByProduct(productId, pageable);
        return ApiResponse.success("Lấy danh sách đánh giá sản phẩm thành công", response);
    }

    @GetMapping("/user")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PageResponse<ReviewResponse>> getReviewsByCurrentUser(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<ReviewResponse> response = reviewService.getReviewsByCurrentUser(pageable);
        return ApiResponse.success("Lấy danh sách đánh giá của bạn thành công", response);
    }

    @GetMapping("/{id}")
    public ApiResponse<ReviewResponse> getReviewById(@PathVariable Long id) {
        ReviewResponse response = reviewService.getReviewById(id);
        return ApiResponse.success("Lấy đánh giá thành công", response);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<ReviewResponse>> getAllReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Integer minRating,
            @RequestParam(required = false) Integer maxRating,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<ReviewResponse> response = reviewService.getAllReviews(pageable, search, status, productId, minRating, maxRating, startDate, endDate);
        return ApiResponse.success("Lấy danh sách đánh giá thành công", response);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ReviewResponse> updateReviewStatus(
            @PathVariable Long id,
            @RequestBody ReviewAdminRequest request) {
        ReviewResponse response = reviewService.updateReviewStatus(id, request.getStatus());
        String message = switch (request.getStatus()) {
            case APPROVED -> "Duyệt đánh giá thành công";
            case HIDDEN -> "Ẩn đánh giá thành công";
            default -> "Cập nhật trạng thái thành công";
        };
        return ApiResponse.success(message, response);
    }

    @PostMapping("/{id}/reply")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ReviewResponse> replyToReview(
            @PathVariable Long id,
            @RequestBody ReviewAdminRequest request) {
        ReviewResponse response = reviewService.replyToReview(id, request.getReply());
        return ApiResponse.success("Phản hồi đánh giá thành công", response);
    }

    @DeleteMapping("/{id}/soft")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> softDeleteReview(@PathVariable Long id) {
        reviewService.updateReviewStatus(id, ReviewStatus.HIDDEN);
        return ApiResponse.success("Ẩn đánh giá thành công", null);
    }

    @GetMapping("/{id}/detail")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<DetailedReviewResponse> getDetailedReview(@PathVariable Long id) {
        DetailedReviewResponse response = reviewService.getDetailedReview(id);
        return ApiResponse.success("Lấy chi tiết đánh giá thành công", response);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ReviewStatsResponse> getReviewStats() {
        ReviewStatsResponse response = reviewService.getReviewStats();
        return ApiResponse.success("Lấy thống kê đánh giá thành công", response);
    }
}