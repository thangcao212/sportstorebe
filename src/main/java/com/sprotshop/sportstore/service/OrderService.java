
package com.sprotshop.sportstore.service;

import com.sprotshop.sportstore.request.CreateOrderRequest;
import com.sprotshop.sportstore.request.OrderSearchRequest;
import com.sprotshop.sportstore.response.OrderResponse;
import com.sprotshop.sportstore.Enum.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {
    OrderResponse createOrderFromCart(CreateOrderRequest request);
    Page<OrderResponse> getUserOrders(Pageable pageable);
    OrderResponse getOrderDetails(Long orderId);
    OrderResponse cancelOrder(Long orderId);
    OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus);
    OrderResponse addTrackingNumber(Long orderId, String trackingNumber);
    OrderResponse getOrderByIdForAdmin(Long orderId);
    Page<OrderResponse> getAllOrders(Pageable pageable);
    Page<OrderResponse> searchOrders(OrderSearchRequest searchRequest, Pageable pageable);
}
