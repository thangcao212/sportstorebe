package com.sprotshop.sportstore.repository;

import com.sprotshop.sportstore.Enum.OrderStatus;
import com.sprotshop.sportstore.entity.Order;
import com.sprotshop.sportstore.entity.OrderItem;
import com.sprotshop.sportstore.entity.Product;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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

}