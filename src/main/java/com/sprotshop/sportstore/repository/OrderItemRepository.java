package com.sprotshop.sportstore.repository;

import com.sprotshop.sportstore.entity.Order;
import com.sprotshop.sportstore.entity.OrderItem;
import com.sprotshop.sportstore.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for OrderItem entity
 * Provides CRUD operations and custom query methods for OrderItem entities
 */
@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    
    /**
     * Find all order items for a specific order
     * @param order the order to find items for
     * @return list of order items for the order
     */
    List<OrderItem> findByOrder(Order order);
    
    /**
     * Find all order items for a specific product
     * @param product the product to find order items for
     * @return list of order items for the product
     */
    List<OrderItem> findByProduct(Product product);
    
    /**
     * Delete all order items for a specific order
     * @param order the order to delete items for
     */
    void deleteByOrder(Order order);
}