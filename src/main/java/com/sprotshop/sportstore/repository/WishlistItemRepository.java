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
    @Query("SELECT i FROM WishlistItem i WHERE i.wishlist.id = :wishlistId AND i.product.id = :productId AND (i.size = :size OR :size IS NULL)")
    Optional<WishlistItem> findByWishlistIdAndProductId(@Param("wishlistId") Long wishlistId, @Param("productId") Long productId);
}