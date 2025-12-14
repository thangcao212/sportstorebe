
package com.sprotshop.sportstore.service;

import com.sprotshop.sportstore.request.*;
import com.sprotshop.sportstore.response.OrderResponse;
import com.sprotshop.sportstore.Enum.OrderStatus;
import com.sprotshop.sportstore.response.PageResponse;
import com.sprotshop.sportstore.response.ProductResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface OrderService {
    OrderResponse createOrderFromCart(CreateOrderRequest request);

    PageResponse<OrderResponse> getUserOrders(Pageable pageable);

    OrderResponse getOrderDetails(Long orderId);

    OrderResponse cancelOrder(Long orderId);

    OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus);

//    OrderResponse addTrackingNumber(Long orderId, String trackingNumber);

    OrderResponse getOrderByIdForAdmin(Long orderId);

    PageResponse<OrderResponse> getAllOrders(Pageable pageable);

    //    Page<OrderResponse> searchOrders(SearchOrderRequest searchRequest, Pageable pageable);
    PageResponse<OrderResponse> searchOrders(OrderSearchRequest request, Pageable pageable);

    void handleSepayWebhook(SepayWebhookRequest webhook);

//    OrderResponse confirmCodPayment(Long orderId);
//
//    OrderResponse confirmProcessing(Long orderId);

    List<OrderResponse> getOrdersByUser(Long userId);

    public List<Map<String, String>> getUniqueUserEmails();

    public List<OrderStatus> getPossibleNextStatuses(Long orderId);

    byte[] exportOrdersToExcel(OrderSearchRequest request);

    List<ProductCardDto> getBestSellingProductsLast30Days(Integer limit);
}
