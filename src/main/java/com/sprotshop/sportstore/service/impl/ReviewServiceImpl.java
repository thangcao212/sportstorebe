package com.sprotshop.sportstore.service.impl;

import com.cloudinary.utils.ObjectUtils;
import com.sprotshop.sportstore.entity.OrderItem;
import com.sprotshop.sportstore.entity.Product;
import com.sprotshop.sportstore.entity.Review;
import com.sprotshop.sportstore.entity.ReviewImage;
import com.sprotshop.sportstore.entity.ReviewReply;
import com.sprotshop.sportstore.entity.User;
import com.sprotshop.sportstore.Enum.OrderStatus;
import com.sprotshop.sportstore.Enum.ReviewStatus;
import com.sprotshop.sportstore.exception.NotFoundException;
import com.sprotshop.sportstore.exception.InvalidReviewException;
import com.sprotshop.sportstore.repository.OrderItemRepository;
import com.sprotshop.sportstore.repository.ReviewReplyRepository;
import com.sprotshop.sportstore.repository.ReviewRepository;
import com.sprotshop.sportstore.repository.ProductRepository;
import com.sprotshop.sportstore.request.ReviewRequest;
import com.sprotshop.sportstore.response.*;
import com.sprotshop.sportstore.service.CloudinaryService;
import com.sprotshop.sportstore.service.ReviewService;
import com.sprotshop.sportstore.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {
    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReviewReplyRepository reviewReplyRepository;
    private final UserService userService;
    private final CloudinaryService cloudinaryService;
    private final CacheManager cacheManager;

    // Trong ReviewServiceImpl.java, update createReview method
    @Override
    @Transactional
    public ReviewResponse createReview(ReviewRequest request, List<MultipartFile> images) {
        try {
            User currentUser = userService.getCurrentLoggedInUser();
            Long userId = currentUser.getId();
            log.info("Creating review for userId: {}, productId: {}", userId, request.getProductId());
            // 1. Validate: User phải đã mua sản phẩm (có OrderItem với Order status COMPLETED)
            List<OrderItem> purchasedItems = orderItemRepository.findByOrder_User_IdAndProduct_IdAndOrder_StatusEquals(
                    userId, request.getProductId(), OrderStatus.COMPLETED);
            if (purchasedItems.isEmpty()) {
                log.warn("User {} has not completed purchase for product {}", userId, request.getProductId());
                throw new InvalidReviewException("Bạn chỉ có thể đánh giá sản phẩm đã mua và hoàn thành.");
            }
//            log.debug("Found {} completed order items for validation", purchasedItems.size());
            // 2. FIXED: Check duplicate review - dùng List để handle multiple, check !isEmpty()
            List<Review> existingReviews = reviewRepository.findByUserIdAndProductId(userId, request.getProductId());
            if (!existingReviews.isEmpty()) {
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
                    // status defaults to APPROVED
                    .build();
            // 4. Handle image uploads
            if (images != null && !images.isEmpty()) {
                for (MultipartFile file : images) {
                    if (!file.isEmpty()) {
                        try {
                            Map uploadResult = cloudinaryService.upload(file);
                            String secureUrl = (String) uploadResult.get("secure_url");
                            String publicId = (String) uploadResult.get("public_id");
                            ReviewImage reviewImage = ReviewImage.builder()
                                    .imageUrl(secureUrl)
                                    .imageId(publicId)
                                    .build();
                            review.addImage(reviewImage);
                        } catch (IOException e) {
                            log.error("Failed to upload image for review", e);
                            throw new RuntimeException("Không thể upload ảnh. Vui lòng thử lại.");
                        }
                    }
                }
            }
            Review savedReview = reviewRepository.save(review);
            log.info("Review created and auto-approved: ID={}", savedReview.getId());
            // Update average rating
            updateProductRating(product.getId());
            return ReviewResponse.fromEntity(savedReview);
        } catch (Exception e) {
            log.error("Create review failed: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional
    public ReviewResponse updateReview(Long reviewId, ReviewRequest request, List<MultipartFile> images) {
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
            // Validate: Vẫn phải đã mua
            List<OrderItem> purchasedItems = orderItemRepository.findByOrder_User_IdAndProduct_IdAndOrder_StatusEquals(
                    userId, review.getProduct().getId(), OrderStatus.COMPLETED);
            if (purchasedItems.isEmpty()) {
                log.warn("User {} no longer has completed purchase for product {}", userId, review.getProduct().getId());
                throw new InvalidReviewException("Không hợp lệ: Sản phẩm chưa được mua.");
            }
            review.setRating(request.getRating());
            review.setComment(request.getComment());
            // Handle new image uploads (replace old ones)
            deleteReviewImages(review.getImages());
            review.getImages().clear();
            if (images != null && !images.isEmpty()) {
                for (MultipartFile file : images) {
                    if (!file.isEmpty()) {
                        try {
                            Map uploadResult = cloudinaryService.upload(file);
                            String secureUrl = (String) uploadResult.get("secure_url");
                            String publicId = (String) uploadResult.get("public_id");
                            ReviewImage reviewImage = ReviewImage.builder()
                                    .imageUrl(secureUrl)
                                    .imageId(publicId)
                                    .build();
                            review.addImage(reviewImage);
                        } catch (IOException e) {
                            log.error("Failed to upload image for review update", e);
                            throw new RuntimeException("Không thể upload ảnh. Vui lòng thử lại.");
                        }
                    }
                }
            }
            Review updatedReview = reviewRepository.save(review);
            log.info("Review updated successfully: ID={}", reviewId);
            // FIXED: Manual evict cache cho product (fallback nếu SpEL fail)
            Long productId = updatedReview.getProduct().getId();
            cacheManager.getCache("productReviews").evict(productId);
            // Update product rating
            updateProductRating(updatedReview.getProduct().getId());
            return ReviewResponse.fromEntity(updatedReview);
        } catch (Exception e) {
            log.error("Update review failed for reviewId {}: {}", reviewId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional
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
            // Xóa ảnh trên Cloudinary
            deleteReviewImages(review.getImages());
            Long productId = review.getProduct().getId();
            reviewRepository.delete(review);
            log.info("Review deleted successfully: ID={}", reviewId);
            // FIXED: Manual evict cache cho product (fallback)
            cacheManager.getCache("productReviews").evict(productId);
            // Update product rating after delete
            updateProductRating(productId);
        } catch (Exception e) {
            log.error("Delete review failed for reviewId {}: {}", reviewId, e.getMessage(), e);
            throw e;
        }
    }

    private void deleteReviewImages(Set<ReviewImage> images) {
        if (images != null && !images.isEmpty()) {
            Set<ReviewImage> imagesCopy = new HashSet<>(images); // Copy để safe remove
            for (ReviewImage image : imagesCopy) {
                String publicId = image.getImageId();
                if (publicId == null || publicId.isEmpty()) {
                    publicId = CloudinaryService.extractPublicIdFromUrl(image.getImageUrl());
                }
                if (publicId != null && !publicId.isEmpty()) {
                    try {
                        cloudinaryService.delete(publicId);
                        log.debug("Deleted image: publicId={}", publicId);
                    } catch (IOException e) {
                        log.error("Delete failed for publicId {}: {}", publicId, e.getMessage(), e);
                        // Không throw, cứ xóa entity thôi
                    }
                } else {
                    log.warn("No publicId for image: {}", image.getImageUrl());
                }
                images.remove(image);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getReviewsByCurrentUser(Pageable pageable) {
        try {
            User currentUser = userService.getCurrentLoggedInUser();
            Long userId = currentUser.getId();
            log.info("Fetching reviews for current userId: {}", userId);
            List<Review> reviewsList = reviewRepository.findByUserIdAndStatus(userId, ReviewStatus.APPROVED);
            // Initialize lazy collections
            for (Review review : reviewsList) {
                Hibernate.initialize(review.getImages());
                Hibernate.initialize(review.getUser());
                Hibernate.initialize(review.getProduct());
            }
            List<Review> pagedReviews = reviewsList.stream()
                    .skip(pageable.getOffset())
                    .limit(pageable.getPageSize())
                    .collect(Collectors.toList());
            Page<Review> reviews = new PageImpl<>(
                    pagedReviews,
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
        // Initialize lazy collections
        Hibernate.initialize(review.getImages());
        Hibernate.initialize(review.getUser());
        Hibernate.initialize(review.getProduct());
        return ReviewResponse.fromEntity(review);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getAllReviews(Pageable pageable, String search, String status, Long productId) {
        log.info("Fetching all reviews with filters: search={}, status={}, productId={}", search, status, productId);
        Specification<Review> spec = Specification.where(null);
        if (search != null && !search.trim().isEmpty()) {
            spec = spec.and((root, query, cb) ->
                    cb.or(
                            cb.like(cb.lower(root.get("comment")), "%" + search.toLowerCase() + "%"),
                            cb.like(cb.lower(root.join("user").get("username")), "%" + search.toLowerCase() + "%"),
                            cb.like(cb.lower(root.join("product").get("name")), "%" + search.toLowerCase() + "%")
                    )
            );
        }
        if (status != null && !status.isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), ReviewStatus.valueOf(status)));
        }
        if (productId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("product").get("id"), productId));
        }
        Page<Review> reviewsPage = reviewRepository.findAll(spec, pageable);
        return PageResponse.fromPage(reviewsPage.map(ReviewResponse::fromEntity));
    }

    @Override
    @Transactional
    public ReviewResponse updateReviewStatus(Long reviewId, ReviewStatus status) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đánh giá: " + reviewId));
        review.setStatus(status);
        Review updated = reviewRepository.save(review);
        // Evict cache
        cacheManager.getCache("productReviews").evict(review.getProduct().getId());
        return ReviewResponse.fromEntity(updated);
    }

    @Override
    @Transactional
    public ReviewResponse replyToReview(Long reviewId, String replyContent) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đánh giá: " + reviewId));
        User adminUser = userService.getCurrentLoggedInUser(); // Assume admin
        ReviewReply reply = ReviewReply.builder()
                .review(review)
                .user(adminUser)
                .content(replyContent)
                .build();
        reviewReplyRepository.save(reply);
        // FIXED: Manual evict cache cho product (fallback)
        Long productId = review.getProduct().getId();
        cacheManager.getCache("productReviews").evict(productId);
        return ReviewResponse.fromEntity(review); // Or enhanced with replies
    }

    @Override
    @Transactional(readOnly = true)
    public DetailedReviewResponse getDetailedReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đánh giá: " + reviewId));
        // Initialize
        Hibernate.initialize(review.getImages());
        Hibernate.initialize(review.getUser());
        Hibernate.initialize(review.getProduct());
        List<ReviewReply> replies = reviewReplyRepository.findByReviewId(reviewId);
        List<ReviewReplyResponse> replyResponses = replies.stream().map(ReviewReplyResponse::fromEntity).collect(Collectors.toList());
        return DetailedReviewResponse.builder()
                .review(ReviewResponse.fromEntity(review))
                .replies(replyResponses)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getAllReviews(Pageable pageable, String search, String status, Long productId, Integer minRating, Integer maxRating, String startDate, String endDate) {
        log.info("Fetching all reviews with advanced filters");
        Specification<Review> spec = Specification.where(null);
        if (search != null && !search.trim().isEmpty()) {
            spec = spec.and((root, query, cb) ->
                    cb.or(
                            cb.like(cb.lower(root.get("comment")), "%" + search.toLowerCase() + "%"),
                            cb.like(cb.lower(root.join("user").get("username")), "%" + search.toLowerCase() + "%"),
                            cb.like(cb.lower(root.join("product").get("name")), "%" + search.toLowerCase() + "%")
                    )
            );
        }
        if (status != null && !status.isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), ReviewStatus.valueOf(status)));
        }
        if (productId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("product").get("id"), productId));
        }
        if (minRating != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("rating"), minRating));
        }
        if (maxRating != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("rating"), maxRating));
        }
        if (startDate != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), LocalDateTime.parse(startDate)));
        }
        if (endDate != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), LocalDateTime.parse(endDate)));
        }
        Page<Review> reviewsPage = reviewRepository.findAll(spec, pageable);
        // Initialize lazy for each
        reviewsPage.getContent().forEach(review -> {
            Hibernate.initialize(review.getImages());
            Hibernate.initialize(review.getUser());
            Hibernate.initialize(review.getProduct());
        });
        return PageResponse.fromPage(reviewsPage.map(ReviewResponse::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewStatsResponse getReviewStats() {
        long total = reviewRepository.count();
        long pending = reviewRepository.countByStatus(ReviewStatus.PENDING);
        long approved = reviewRepository.countByStatus(ReviewStatus.APPROVED);
        long hidden = reviewRepository.countByStatus(ReviewStatus.HIDDEN);
        // Top products by review count
        List<Object[]> topProductsRaw = reviewRepository.findTopProductsByReviewCount(10);
        List<ProductReviewCount> topProducts = topProductsRaw.stream().map(row ->
                ProductReviewCount.builder()
                        .productId((Long) row[0])
                        .productName((String) row[1])
                        .reviewCount((Long) row[2])
                        .build()
        ).collect(Collectors.toList());
        // Average rating
        Double avgRating = reviewRepository.findAverageRating();
        // Sentiment
        long positive = reviewRepository.countByRatingBetween(4, 5);
        long neutral = reviewRepository.countByRatingEquals(3);
        long negative = reviewRepository.countByRatingBetween(1, 2);
        return ReviewStatsResponse.builder()
                .totalReviews(total)
                .pendingReviews(pending)
                .approvedReviews(approved)
                .hiddenReviews(hidden)
                .topProducts(topProducts)
                .averageRating(avgRating)
                .sentimentStats(SentimentStats.builder()
                        .positive(positive)
                        .neutral(neutral)
                        .negative(negative)
                        .build())
                .build();
    }

    private void updateProductRating(Long productId) {
        List<Review> approvedReviews = reviewRepository.findByProductIdAndStatus(productId, ReviewStatus.APPROVED);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found: " + productId));

        if (approvedReviews.isEmpty()) {
            product.setAverageRating(BigDecimal.ZERO);
            product.setReviewCount(0);
        } else {
            double tempAvg = approvedReviews.stream()
                    .mapToInt(Review::getRating)
                    .average()
                    .orElse(0.0);

            BigDecimal avgRating = BigDecimal.valueOf(tempAvg)
                    .setScale(2, RoundingMode.HALF_UP); // Đây chính là dòng bạn cần!

            product.setAverageRating(avgRating);
            product.setReviewCount(approvedReviews.size());
        }

        productRepository.save(product);
        log.info("Đã cập nhật rating sản phẩm {}: {} ⭐ ({} đánh giá)",
                productId, product.getAverageRating(), product.getReviewCount());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductReviewWithRepliesResponse> getReviewsByProduct(Long productId, Pageable pageable) {
        log.info("Fetching APPROVED reviews with replies for productId: {} (from CACHE/DB)", productId);  // FIXED: Log để debug cache hit
        List<Review> reviewsList = reviewRepository.findByProductIdAndStatus(productId, ReviewStatus.APPROVED);
        // Initialize lazy collections cho tất cả reviews
        for (Review review : reviewsList) {
            Hibernate.initialize(review.getImages());
            Hibernate.initialize(review.getUser());
            Hibernate.initialize(review.getProduct());
        }
        // Tạo list responses với replies
        List<ProductReviewWithRepliesResponse> responseList = reviewsList.stream()
                .map(this::buildReviewWithReplies)
                .skip(pageable.getOffset())
                .limit(pageable.getPageSize())
                .collect(Collectors.toList());
        // Tạo page thủ công
        long total = reviewsList.size();
        Page<ProductReviewWithRepliesResponse> pagedReviews = new PageImpl<>(responseList, pageable, total);
        return PageResponse.fromPage(pagedReviews);
    }

    private ProductReviewWithRepliesResponse buildReviewWithReplies(Review review) {
        // Fetch replies
        List<ReviewReply> replies = reviewReplyRepository.findByReviewId(review.getId());
        List<ReviewReplyResponse> replyResponses = replies.stream()
                .map(ReviewReplyResponse::fromEntity)
                .collect(Collectors.toList());
        return ProductReviewWithRepliesResponse.builder()
                .review(ReviewResponse.fromEntity(review))
                .replies(replyResponses)
                .build();
    }
}