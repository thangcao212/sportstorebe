// Updated WishlistRepository.java - Add method for duplicate check (if not exist)
package com.sprotshop.sportstore.repository;

import com.sprotshop.sportstore.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {
    boolean existsByWishlistIdAndProductId(Long wishlistId, Long productId);
    // hoặc
    Optional<WishlistItem> findByWishlistIdAndProductId(Long wishlistId, Long productId);
}