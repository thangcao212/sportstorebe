package com.sprotshop.sportstore.controller;

import com.sprotshop.sportstore.Enum.OrderStatus;
import com.sprotshop.sportstore.request.CreateOrderRequest;
import com.sprotshop.sportstore.response.ApiResponse;
import com.sprotshop.sportstore.response.PageResponse; // Import PageResponse của bạn
import com.sprotshop.sportstore.response.OrderResponse;
import com.sprotshop.sportstore.service.OrderService;
import com.sprotshop.sportstore.exception.NotFoundException; // Import exception
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);
    private final OrderService orderService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {
        log.info("POST /api/orders - Current user creating order.");
        try {
            OrderResponse createdOrder = orderService.createOrderFromCart(request);
            ApiResponse<OrderResponse> response = ApiResponse.<OrderResponse>builder()
                    .message("Order created successfully.") // Bỏ phần payment ra khỏi message mặc định
                    .data(createdOrder)
                    .status(HttpStatus.CREATED.value())
                    .build();
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.<OrderResponse>builder()
                    .message(e.getMessage()).status(HttpStatus.BAD_REQUEST.value()).build());
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.<OrderResponse>builder()
                    .message(e.getMessage()).status(HttpStatus.NOT_FOUND.value()).build());
        }
    }

    @GetMapping("/my-history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getUserOrderHistory(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("GET /api/orders/my-history - Fetching order history.");
        Page<OrderResponse> orderPage = orderService.getUserOrders(pageable);
        PageResponse<OrderResponse> pageResponse = PageResponse.<OrderResponse>builder()
                .data(orderPage.getContent())
                .currentPage(orderPage.getNumber())
                .pageSize(orderPage.getSize())
                .totalElements(orderPage.getTotalElements())
                .totalPages(orderPage.getTotalPages())
                .build();
        ApiResponse<PageResponse<OrderResponse>> response = ApiResponse.<PageResponse<OrderResponse>>builder()
                .message("Success")
                .data(pageResponse)
                .status(HttpStatus.OK.value())
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderDetails(
            @PathVariable Long orderId) {
        log.info("GET /api/orders/{} - Fetching order details.", orderId);
        try {
            OrderResponse orderResponse = orderService.getOrderDetails(orderId);
            ApiResponse<OrderResponse> response = ApiResponse.<OrderResponse>builder()
                    .message("Success")
                    .data(orderResponse)
                    .status(HttpStatus.OK.value())
                    .build();
            return ResponseEntity.ok(response);
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.<OrderResponse>builder()
                    .message(e.getMessage()).status(HttpStatus.NOT_FOUND.value()).build());
        }
    }

    @PutMapping("/{orderId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelMyOrder(
            @PathVariable Long orderId) {
        log.warn("PUT /api/orders/{}/cancel - User requesting cancellation.", orderId);
        try {
            OrderResponse canceledOrder = orderService.cancelOrder(orderId);
            ApiResponse<OrderResponse> response = ApiResponse.<OrderResponse>builder()
                    .message("Order canceled successfully.")
                    .data(canceledOrder)
                    .status(HttpStatus.OK.value())
                    .build();
            return ResponseEntity.ok(response);
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.<OrderResponse>builder()
                    .message(e.getMessage()).status(HttpStatus.NOT_FOUND.value()).build());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.<OrderResponse>builder()
                    .message(e.getMessage()).status(HttpStatus.CONFLICT.value()).build());
        }
    }

    // ================= ADMIN ENDPOINTS =================
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getAllOrdersForAdmin(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<OrderResponse> orderPage = orderService.getAllOrders(pageable);
        PageResponse<OrderResponse> pageResponse = PageResponse.<OrderResponse>builder()
                .data(orderPage.getContent()).currentPage(orderPage.getNumber())
                .pageSize(orderPage.getSize()).totalElements(orderPage.getTotalElements())
                .totalPages(orderPage.getTotalPages()).build();
        ApiResponse<PageResponse<OrderResponse>> response = ApiResponse.<PageResponse<OrderResponse>>builder()
                .message("Success").data(pageResponse).status(HttpStatus.OK.value()).build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/{orderId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderDetailsForAdmin(
            @PathVariable Long orderId) {
        try {
            OrderResponse orderResponse = orderService.getOrderByIdForAdmin(orderId);
            ApiResponse<OrderResponse> response = ApiResponse.<OrderResponse>builder()
                    .message("Success").data(orderResponse).status(HttpStatus.OK.value()).build();
            return ResponseEntity.ok(response);
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.<OrderResponse>builder()
                    .message(e.getMessage()).status(HttpStatus.NOT_FOUND.value()).build());
        }
    }

    @PutMapping("/admin/{orderId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable Long orderId,
            @RequestBody Map<String, String> statusUpdate) {
        String newStatusString = statusUpdate.get("status");
        if (!StringUtils.hasText(newStatusString)) {
            return ResponseEntity.badRequest().body(ApiResponse.<OrderResponse>builder()
                    .message("Status value is required.").status(HttpStatus.BAD_REQUEST.value()).build());
        }
        log.warn("[ADMIN] PUT /api/orders/admin/{}/status - Updating status to: {}", orderId, newStatusString);
        try {
            OrderStatus newStatus = OrderStatus.valueOf(newStatusString.toUpperCase());
            OrderResponse updatedOrder = orderService.updateOrderStatus(orderId, newStatus);
            ApiResponse<OrderResponse> response = ApiResponse.<OrderResponse>builder()
                    .message("Order status updated successfully.").data(updatedOrder).status(HttpStatus.OK.value()).build();
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.<OrderResponse>builder()
                    .message("Invalid status value provided: " + newStatusString).status(HttpStatus.BAD_REQUEST.value()).build());
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.<OrderResponse>builder()
                    .message(e.getMessage()).status(HttpStatus.NOT_FOUND.value()).build());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.<OrderResponse>builder()
                    .message(e.getMessage()).status(HttpStatus.CONFLICT.value()).build());
        }
    }

    @PutMapping("/admin/{orderId}/tracking")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> addTrackingNumber(
            @PathVariable Long orderId,
            @RequestBody Map<String, String> trackingUpdate) {
        String trackingNumber = trackingUpdate.get("trackingNumber");
        if (!StringUtils.hasText(trackingNumber)) {
            return ResponseEntity.badRequest().body(ApiResponse.<OrderResponse>builder()
                    .message("Tracking number cannot be empty.").status(HttpStatus.BAD_REQUEST.value()).build());
        }
        log.info("[ADMIN] PUT /api/orders/admin/{}/tracking - Adding tracking number: {}", orderId, trackingNumber);
        try {
            OrderResponse updatedOrder = orderService.addTrackingNumber(orderId, trackingNumber);
            ApiResponse<OrderResponse> response = ApiResponse.<OrderResponse>builder()
                    .message("Tracking number added/updated successfully.").data(updatedOrder).status(HttpStatus.OK.value()).build();
            return ResponseEntity.ok(response);
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.<OrderResponse>builder()
                    .message(e.getMessage()).status(HttpStatus.NOT_FOUND.value()).build());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.<OrderResponse>builder()
                    .message(e.getMessage()).status(HttpStatus.CONFLICT.value()).build());
        }
    }
}