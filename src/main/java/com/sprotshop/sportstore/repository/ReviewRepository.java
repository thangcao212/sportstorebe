package com.sprotshop.sportstore.repository;

import com.sprotshop.sportstore.entity.Review;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review,Long> {
    Optional<Object> findByUserIdAndProductId(Long userId, @NotNull(message = "Product ID không được để trống") Long productId);

    List<Review> findByProductId(Long productId);

    List<Review> findByUserId(Long userId);
}
