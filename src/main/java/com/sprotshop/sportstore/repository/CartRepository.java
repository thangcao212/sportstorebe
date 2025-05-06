package com.sprotshop.sportstore.repository;

import com.sprotshop.sportstore.entity.Cart;
import com.sprotshop.sportstore.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for Cart entity
 * Provides CRUD operations and custom query methods for Cart entities
 */
@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {
    
    /**
     * Find a cart by user
     * @param user the user whose cart to find
     * @return the cart belonging to the user, or empty if not found
     */
    @Query("SELECT c FROM Cart c LEFT JOIN FETCH c.cartItems ci LEFT JOIN FETCH ci.product p WHERE c.user.id = :userId")
    Optional<Cart> fetchCartWithItemsByUserId(@Param("userId") Long userId);



    Optional<Cart> findByUserId(Long id);
}