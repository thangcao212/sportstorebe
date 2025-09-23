
package com.sprotshop.sportstore.service;

import com.sprotshop.sportstore.request.CreateOrderRequest;
import com.sprotshop.sportstore.request.OrderSearchRequest;
import com.sprotshop.sportstore.response.OrderResponse;
import com.sprotshop.sportstore.Enum.OrderStatus;
import com.sprotshop.sportstore.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface OrderService {
    OrderResponse createOrderFromCart(CreateOrderRequest request);

    PageResponse<OrderResponse> getUserOrders(Pageable pageable);

    OrderResponse getOrderDetails(Long orderId);

    OrderResponse cancelOrder(Long orderId);

    OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus);

    OrderResponse addTrackingNumber(Long orderId, String trackingNumber);

    OrderResponse getOrderByIdForAdmin(Long orderId);

    PageResponse<OrderResponse> getAllOrders(Pageable pageable);

    //    Page<OrderResponse> searchOrders(SearchOrderRequest searchRequest, Pageable pageable);
    PageResponse<OrderResponse> searchOrders(OrderSearchRequest request, Pageable pageable);

}
