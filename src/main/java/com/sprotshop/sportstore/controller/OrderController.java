
        package com.sprotshop.sportstore.controller;

import com.sprotshop.sportstore.Enum.OrderStatus;
import com.sprotshop.sportstore.Enum.PaymentMethod;
import com.sprotshop.sportstore.request.CreateOrderRequest;
import com.sprotshop.sportstore.request.OrderSearchRequest;
import com.sprotshop.sportstore.response.ApiResponse;
import com.sprotshop.sportstore.response.PageResponse;
import com.sprotshop.sportstore.response.OrderResponse;
import com.sprotshop.sportstore.service.OrderService;
import com.sprotshop.sportstore.exception.NotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);
    private final OrderService orderService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        try {
            OrderResponse order = orderService.createOrderFromCart(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.<OrderResponse>builder()
                            .message("Order created successfully")
                            .data(order)
                            .status(HttpStatus.CREATED.value())
                            .build());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<OrderResponse>builder()
                            .message(e.getMessage())
                            .status(HttpStatus.BAD_REQUEST.value())
                            .build());
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.<OrderResponse>builder()
                            .message(e.getMessage())
                            .status(HttpStatus.NOT_FOUND.value())
                            .build());
        }
    }

    @GetMapping("/my-history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getUserOrderHistory(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<OrderResponse> orderPage = orderService.getUserOrders(pageable);
        PageResponse<OrderResponse> pageResponse = PageResponse.<OrderResponse>builder()
                .data(orderPage.getContent())
                .currentPage(orderPage.getNumber())
                .pageSize(orderPage.getSize())
                .totalElements(orderPage.getTotalElements())
                .totalPages(orderPage.getTotalPages())
                .build();
        return ResponseEntity.ok(ApiResponse.<PageResponse<OrderResponse>>builder()
                .message("Success")
                .data(pageResponse)
                .status(HttpStatus.OK.value())
                .build());
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderDetails(@PathVariable Long orderId) {
        try {
            OrderResponse order = orderService.getOrderDetails(orderId);
            return ResponseEntity.ok(ApiResponse.<OrderResponse>builder()
                    .message("Success")
                    .data(order)
                    .status(HttpStatus.OK.value())
                    .build());
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.<OrderResponse>builder()
                            .message(e.getMessage())
                            .status(HttpStatus.NOT_FOUND.value())
                            .build());
        }
    }

    @PutMapping("/{orderId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(@PathVariable Long orderId) {
        try {
            OrderResponse order = orderService.cancelOrder(orderId);
            return ResponseEntity.ok(ApiResponse.<OrderResponse>builder()
                    .message("Order canceled successfully")
                    .data(order)
                    .status(HttpStatus.OK.value())
                    .build());
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.<OrderResponse>builder()
                            .message(e.getMessage())
                            .status(HttpStatus.NOT_FOUND.value())
                            .build());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.<OrderResponse>builder()
                            .message(e.getMessage())
                            .status(HttpStatus.CONFLICT.value())
                            .build());
        }
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getAllOrders(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<OrderResponse> orderPage = orderService.getAllOrders(pageable);
        PageResponse<OrderResponse> pageResponse = PageResponse.<OrderResponse>builder()
                .data(orderPage.getContent())
                .currentPage(orderPage.getNumber())
                .pageSize(orderPage.getSize())
                .totalElements(orderPage.getTotalElements())
                .totalPages(orderPage.getTotalPages())
                .build();
        return ResponseEntity.ok(ApiResponse.<PageResponse<OrderResponse>>builder()
                .message("Success")
                .data(pageResponse)
                .status(HttpStatus.OK.value())
                .build());
    }

    @GetMapping("/admin/{orderId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderDetailsForAdmin(@PathVariable Long orderId) {
        try {
            OrderResponse order = orderService.getOrderByIdForAdmin(orderId);
            return ResponseEntity.ok(ApiResponse.<OrderResponse>builder()
                    .message("Success")
                    .data(order)
                    .status(HttpStatus.OK.value())
                    .build());
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.<OrderResponse>builder()
                            .message(e.getMessage())
                            .status(HttpStatus.NOT_FOUND.value())
                            .build());
        }
    }

    @PutMapping("/admin/{orderId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable Long orderId, @RequestBody Map<String, String> request) {
        try {
            OrderStatus newStatus = OrderStatus.valueOf(request.get("status"));
            OrderResponse order = orderService.updateOrderStatus(orderId, newStatus);
            return ResponseEntity.ok(ApiResponse.<OrderResponse>builder()
                    .message("Status updated successfully")
                    .data(order)
                    .status(HttpStatus.OK.value())
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<OrderResponse>builder()
                            .message("Invalid status")
                            .status(HttpStatus.BAD_REQUEST.value())
                            .build());
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.<OrderResponse>builder()
                            .message(e.getMessage())
                            .status(HttpStatus.NOT_FOUND.value())
                            .build());
        }
    }

    @PutMapping("/admin/{orderId}/tracking")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> addTrackingNumber(
            @PathVariable Long orderId, @RequestBody Map<String, String> request) {
        String trackingNumber = request.get("trackingNumber");
        if (!StringUtils.hasText(trackingNumber)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<OrderResponse>builder()
                            .message("Tracking number is required")
                            .status(HttpStatus.BAD_REQUEST.value())
                            .build());
        }
        try {
            OrderResponse order = orderService.addTrackingNumber(orderId, trackingNumber);
            return ResponseEntity.ok(ApiResponse.<OrderResponse>builder()
                    .message("Tracking number added")
                    .data(order)
                    .status(HttpStatus.OK.value())
                    .build());
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.<OrderResponse>builder()
                            .message(e.getMessage())
                            .status(HttpStatus.NOT_FOUND.value())
                            .build());
        }
    }

    @GetMapping("/admin/search")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> searchOrders(
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String userEmail,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdAtFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdAtTo,
            @RequestParam(required = false) BigDecimal minTotalAmount,
            @RequestParam(required = false) BigDecimal maxTotalAmount,
            @RequestParam(required = false) String shippingCity,
            @RequestParam(required = false) String shippingDistrict,
            @RequestParam(required = false) String shippingWard,
            @RequestParam(required = false) String trackingNumber,
            @RequestParam(required = false) Integer provinceCode,
            @RequestParam(required = false) Integer districtCode,
            @RequestParam(required = false) Integer wardCode,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        OrderSearchRequest request = OrderSearchRequest.builder()
                .orderId(orderId)
                .userId(userId)
                .userEmail(userEmail)
                .status(status)
                .paymentMethod(paymentMethod)
                .paymentStatus(paymentStatus)
                .createdAtFrom(createdAtFrom)
                .createdAtTo(createdAtTo)
                .minTotalAmount(minTotalAmount)
                .maxTotalAmount(maxTotalAmount)
                .shippingCity(shippingCity)
                .shippingDistrict(shippingDistrict)
                .shippingWard(shippingWard)
                .trackingNumber(trackingNumber)
                .provinceCode(provinceCode)
                .districtCode(districtCode)
                .wardCode(wardCode)
                .build();
        Page<OrderResponse> orderPage = orderService.searchOrders(request, pageable);
        PageResponse<OrderResponse> pageResponse = PageResponse.<OrderResponse>builder()
                .data(orderPage.getContent())
                .currentPage(orderPage.getNumber())
                .pageSize(orderPage.getSize())
                .totalElements(orderPage.getTotalElements())
                .totalPages(orderPage.getTotalPages())
                .build();
        return ResponseEntity.ok(ApiResponse.<PageResponse<OrderResponse>>builder()
                .message("Orders searched successfully")
                .data(pageResponse)
                .status(HttpStatus.OK.value())
                .build());
    }


}
