package com.sprotshop.sportstore.repository;

import com.sprotshop.sportstore.Enum.OrderStatus;
import com.sprotshop.sportstore.entity.Order;
import com.sprotshop.sportstore.entity.OrderItem;
import com.sprotshop.sportstore.entity.Product;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository interface for OrderItem entity
 * Provides CRUD operations and custom query methods for OrderItem entities
 */
@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

//
//    List<OrderItem> findCompletedOrderItemsByUserAndProduct(Long userId, @NotNull(message = "Product ID không được để trống") Long productId);

    List<OrderItem> findByOrder_User_IdAndProduct_IdAndOrder_StatusEquals(Long userId, Long productId, OrderStatus status);

    // 👈 NEW: Native query cho top product IDs by sold (quantity sum)
    @Query(value = """
    SELECT oi.product_id, SUM(oi.quantity) as total_sold 
    FROM order_items oi 
    JOIN orders o ON oi.order_id = o.id 
    WHERE o.status = 'COMPLETED' 
      AND o.created_at >= :cutoff 
    GROUP BY oi.product_id 
    ORDER BY total_sold DESC 
    LIMIT :limit
    """, nativeQuery = true)
    List<Object[]> findTopProductIdsBySold(@Param("limit") int limit, @Param("cutoff") LocalDateTime cutoff);
}