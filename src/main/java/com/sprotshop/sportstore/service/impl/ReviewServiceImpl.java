package com.sprotshop.sportstore.service.impl;

import com.sprotshop.sportstore.entity.OrderItem;
import com.sprotshop.sportstore.entity.Product;
import com.sprotshop.sportstore.entity.Review;
import com.sprotshop.sportstore.entity.User;
import com.sprotshop.sportstore.Enum.OrderStatus;  // Thêm import này
import com.sprotshop.sportstore.exception.NotFoundException;
import com.sprotshop.sportstore.exception.InvalidReviewException;
import com.sprotshop.sportstore.repository.OrderItemRepository;
import com.sprotshop.sportstore.repository.ReviewRepository;
import com.sprotshop.sportstore.repository.ProductRepository;
import com.sprotshop.sportstore.request.ReviewRequest;
import com.sprotshop.sportstore.response.PageResponse;
import com.sprotshop.sportstore.response.ReviewResponse;
import com.sprotshop.sportstore.service.ReviewService;
import com.sprotshop.sportstore.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserService userService;

    @Override
    @Transactional
    @CacheEvict(value = "productReviews", key = "#request.productId")
    public ReviewResponse createReview(ReviewRequest request) {
        try {
            User currentUser = userService.getCurrentLoggedInUser();
            Long userId = currentUser.getId();
            log.info("Creating review for userId: {}, productId: {}", userId, request.getProductId());

            // 1. Validate: User phải đã mua sản phẩm (có OrderItem với Order status COMPLETED)
            List<OrderItem> purchasedItems = orderItemRepository.findByOrder_User_IdAndProduct_IdAndOrder_StatusEquals(
                    userId, request.getProductId(), OrderStatus.COMPLETED);  // Sửa: Thêm param status và đổi tên method
            if (purchasedItems.isEmpty()) {
                log.warn("User {} has not completed purchase for product {}", userId, request.getProductId());
                throw new InvalidReviewException("Bạn chỉ có thể đánh giá sản phẩm đã mua và hoàn thành.");
            }
            log.debug("Found {} completed order items for validation", purchasedItems.size());

            // 2. Check duplicate review
            if (reviewRepository.findByUserIdAndProductId(userId, request.getProductId()).isPresent()) {
                throw new InvalidReviewException("Bạn đã đánh giá sản phẩm này rồi.");
            }

            // 3. Get entities
            Product product = productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy sản phẩm: " + request.getProductId()));

            Review review = Review.builder()
                    .user(currentUser)
                    .product(product)
                    .rating(request.getRating())
                    .comment(request.getComment())
                    .build();

            Review savedReview = reviewRepository.save(review);
            log.info("Review created successfully: ID={}", savedReview.getId());

            // Update average rating for product if needed (optional)
            updateProductRating(product.getId());

            return ReviewResponse.fromEntity(savedReview);
        } catch (Exception e) {
            log.error("Create review failed: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = {"userReviews", "productReviews"}, key = "#reviewId")
    public ReviewResponse updateReview(Long reviewId, ReviewRequest request) {
        try {
            User currentUser = userService.getCurrentLoggedInUser();
            Long userId = currentUser.getId();
            log.info("Updating reviewId: {} for userId: {}", reviewId, userId);

            Review review = reviewRepository.findById(reviewId)
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy nhận xét: " + reviewId));

            // Validate: Chỉ owner mới update
            if (!review.getUser().getId().equals(userId)) {
                throw new InvalidReviewException("Bạn không có quyền chỉnh sửa nhận xét này.");
            }

            // Validate: Vẫn phải đã mua (giữ nguyên logic, sửa tương tự)
            List<OrderItem> purchasedItems = orderItemRepository.findByOrder_User_IdAndProduct_IdAndOrder_StatusEquals(
                    userId, review.getProduct().getId(), OrderStatus.COMPLETED);  // Sửa: Thêm param status
            if (purchasedItems.isEmpty()) {
                log.warn("User {} no longer has completed purchase for product {}", userId, review.getProduct().getId());
                throw new InvalidReviewException("Không hợp lệ: Sản phẩm chưa được mua.");
            }

            review.setRating(request.getRating());
            review.setComment(request.getComment());

            Review updatedReview = reviewRepository.save(review);
            log.info("Review updated successfully: ID={}", reviewId);

            // Update product rating
            updateProductRating(review.getProduct().getId());

            return ReviewResponse.fromEntity(updatedReview);
        } catch (Exception e) {
            log.error("Update review failed for reviewId {}: {}", reviewId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = {"userReviews", "productReviews"}, allEntries = true)
    public void deleteReview(Long reviewId) {
        try {
            User currentUser = userService.getCurrentLoggedInUser();
            Long userId = currentUser.getId();
            log.info("Deleting reviewId: {} for userId: {}", reviewId, userId);

            Review review = reviewRepository.findById(reviewId)
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy nhận xét: " + reviewId));

            // Validate: Chỉ owner hoặc admin delete
            if (!review.getUser().getId().equals(userId)) {
                throw new InvalidReviewException("Bạn không có quyền xóa nhận xét này.");
            }

            Long productId = review.getProduct().getId();
            reviewRepository.delete(review);
            log.info("Review deleted successfully: ID={}", reviewId);

            // Update product rating after delete
            updateProductRating(productId);
        } catch (Exception e) {
            log.error("Delete review failed for reviewId {}: {}", reviewId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "productReviews", key = "#productId")
    public PageResponse<ReviewResponse> getReviewsByProduct(Long productId, Pageable pageable) {
        log.info("Fetching reviews for productId: {}", productId);
        // Assuming repo supports paging, or use PageImpl like before
        List<Review> reviewsList = reviewRepository.findByProductId(productId);
        Page<Review> reviews = new PageImpl<>(
                reviewsList.stream()
                        .skip(pageable.getOffset())
                        .limit(pageable.getPageSize())
                        .collect(Collectors.toList()),
                pageable,
                reviewsList.size()
        );
        return PageResponse.fromPage(reviews.map(ReviewResponse::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "userReviews", key = "#root.target.userService.getCurrentLoggedInUser().id")
    public PageResponse<ReviewResponse> getReviewsByCurrentUser(Pageable pageable) {
        try {
            User currentUser = userService.getCurrentLoggedInUser();
            Long userId = currentUser.getId();
            log.info("Fetching reviews for current userId: {}", userId);
            List<Review> reviewsList = reviewRepository.findByUserId(userId);
            Page<Review> reviews = new PageImpl<>(
                    reviewsList.stream()
                            .skip(pageable.getOffset())
                            .limit(pageable.getPageSize())
                            .collect(Collectors.toList()),
                    pageable,
                    reviewsList.size()
            );
            return PageResponse.fromPage(reviews.map(ReviewResponse::fromEntity));
        } catch (Exception e) {
            log.error("Get user reviews failed: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewResponse getReviewById(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy nhận xét: " + reviewId));
        return ReviewResponse.fromEntity(review);
    }

    // Helper: Update average rating for product (optional)
    private void updateProductRating(Long productId) {
        List<Review> reviews = reviewRepository.findByProductId(productId);
        if (!reviews.isEmpty()) {
            double avgRating = reviews.stream()
                    .mapToInt(Review::getRating)
                    .average()
                    .orElse(0.0);
            // Update Product if has averageRating field
            Product product = productRepository.findById(productId).orElseThrow();
            // product.setAverageRating((float) avgRating);  // Uncomment nếu Product có field này
            productRepository.save(product);
            log.debug("Updated average rating for product {}: {}", productId, avgRating);
        }
    }
}