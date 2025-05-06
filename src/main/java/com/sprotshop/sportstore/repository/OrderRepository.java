package com.sprotshop.sportstore.repository;

import com.sprotshop.sportstore.Enum.OrderStatus;
import com.sprotshop.sportstore.entity.Order;
import com.sprotshop.sportstore.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Order entity
 * Provides CRUD operations and custom query methods for Order entities
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Tìm các đơn hàng của một user, có phân trang và sắp xếp.
     * Load sẵn user và orderItems (kèm product) để tránh N+1 query.
     */
    @EntityGraph(attributePaths = {"user", "orderItems", "orderItems.product"})
    Page<Order> findByUserId(Long userId, Pageable pageable);

    /**
     * Tìm một đơn hàng cụ thể theo ID và đảm bảo nó thuộc về user.
     * Load sẵn các thông tin chi tiết cần thiết.
     */
    @EntityGraph(attributePaths = {"user", "orderItems", "orderItems.product", "orderItems.product.images"})
    Optional<Order> findByIdAndUserId(Long orderId, Long userId);

    /**
     * Ghi đè findById để luôn load các thông tin cần thiết khi xem chi tiết đơn hàng.
     */
    @Override
    @EntityGraph(attributePaths = {"user", "orderItems", "orderItems.product", "orderItems.product.images"})
    Optional<Order> findById(Long orderId);

    /**
     * Ghi đè findAll(Pageable) để load sẵn thông tin chi tiết khi admin xem tất cả đơn hàng.
     */
    @Override
    @EntityGraph(attributePaths = {"user", "orderItems", "orderItems.product", "orderItems.product.images"})
    Page<Order> findAll(Pageable pageable);

}