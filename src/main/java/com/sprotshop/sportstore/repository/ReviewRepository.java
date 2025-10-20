// File: com/sprotshop/sportstore/repository/ReviewRepository.java
package com.sprotshop.sportstore.repository;

import com.sprotshop.sportstore.Enum.ReviewStatus;
import com.sprotshop.sportstore.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {


    List<Review> findByProductId(Long productId);

    List<Review> findByUserId(Long userId);

    List<Review> findByProductIdAndStatus(Long productId, ReviewStatus status);

    List<Review> findByUserIdAndStatus(Long userId, ReviewStatus status);

    long countByStatus(ReviewStatus status);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.status = 'APPROVED'")
    Double findAverageRating();

    @Query("SELECT p.id, p.name, COUNT(r) FROM Product p JOIN p.reviews r WHERE r.status = 'APPROVED' GROUP BY p.id, p.name ORDER BY COUNT(r) DESC")
    List<Object[]> findTopProductsByReviewCount(int limit);

    long countByRatingBetween(int min, int max);

    long countByRatingEquals(int rating);

    Page<Review> findAll(Specification<Review> spec, Pageable pageable);

    List<Review> findByUserIdAndProductId(Long userId, Long productId);
}