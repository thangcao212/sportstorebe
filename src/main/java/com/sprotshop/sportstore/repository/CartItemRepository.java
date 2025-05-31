package com.sprotshop.sportstore.repository;

import com.sprotshop.sportstore.entity.Cart;
import com.sprotshop.sportstore.entity.CartItem;
import com.sprotshop.sportstore.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for CartItem entity
 * Provides CRUD operations and custom query methods for CartItem entities
 */
@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    Optional<CartItem> findByCartAndProduct(Cart cart, Product product);

    Optional<CartItem> findByCartAndProductAndSize(Cart cart, Product product, String size);


    Optional<CartItem> findByIdAndCart(Long id, Cart cart);

    // Xóa các item theo Cart (dùng trong clearCart)
    void deleteAllByCart(Cart cart);

    // Tìm tất cả item theo Cart (nếu không dùng @EntityGraph từ CartRepo)
    // @EntityGraph(attributePaths = {"product", "product.images"}) // Load product và ảnh nếu cần
    // Set<CartItem> findAllByCart(Cart cart);
}