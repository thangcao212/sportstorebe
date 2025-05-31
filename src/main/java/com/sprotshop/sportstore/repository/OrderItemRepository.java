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
    

}