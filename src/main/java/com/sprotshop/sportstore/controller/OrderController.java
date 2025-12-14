// Updated OrderController.java - Split into separate @Controller for view rendering (getCheckoutPage). Rest remains @RestController.
// Also, fixed checkPaymentStatus to use orderId from path, not body (matching AJAX in demo). Added query param support if needed.
package com.sprotshop.sportstore.controller;

import com.sprotshop.sportstore.Enum.OrderStatus;
import com.sprotshop.sportstore.Enum.PaymentMethod;
import com.sprotshop.sportstore.Enum.PaymentStatus;
import com.sprotshop.sportstore.entity.Order;
import com.sprotshop.sportstore.entity.User;
import com.sprotshop.sportstore.exception.InvalidOrderTransitionException;
import com.sprotshop.sportstore.repository.OrderRepository;
import com.sprotshop.sportstore.request.*;
import com.sprotshop.sportstore.response.ApiResponse;
import com.sprotshop.sportstore.response.PageResponse;
import com.sprotshop.sportstore.response.OrderResponse;
import com.sprotshop.sportstore.service.OrderService;
import com.sprotshop.sportstore.service.ProvinceService;
import com.sprotshop.sportstore.exception.NotFoundException;
import com.sprotshop.sportstore.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);
    private final OrderService orderService;
    private final ProvinceService provinceService;
    private final OrderRepository orderRepository;
    private final UserService userService;

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
        PageResponse<OrderResponse> pageResponse = orderService.getUserOrders(pageable);
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
        PageResponse<OrderResponse> pageResponse = orderService.getAllOrders(pageable);
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
        } catch (InvalidOrderTransitionException e) {  // Handle flow errors as 409
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.<OrderResponse>builder()
                            .message("Lỗi flow: " + e.getMessage())
                            .status(HttpStatus.CONFLICT.value())
                            .build());
        }
    }


    @PostMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> searchOrders(
            @RequestBody OrderSearchRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("Searching orders with request: {}, page: {}, size: {}", request, page, size);
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<OrderResponse> response = orderService.searchOrders(request, pageable);
        return ResponseEntity.ok(ApiResponse.<PageResponse<OrderResponse>>builder()
                .message("Orders searched successfully")
                .data(response)
                .status(HttpStatus.OK.value())
                .build());
    }

    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> syncData() {
        try {
            provinceService.syncAllData();
            return ResponseEntity.ok(ApiResponse.<String>builder()
                    .message("Data synchronization started")
                    .data("Sync in progress")
                    .status(HttpStatus.OK.value())
                    .build());
        } catch (Exception e) {
            log.error("Error starting sync", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<String>builder()
                            .message("Error starting sync: " + e.getMessage())
                            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .build());
        }
    }

    @GetMapping("/{orderId}/checkout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCheckoutDetails(@PathVariable Long orderId) {
        try {
            Order order = orderRepository.findByIdAndUserId(orderId, userService.getCurrentLoggedInUser().getId())
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng: " + orderId));

            Map<String, Object> checkoutData = new HashMap<>();
            checkoutData.put("orderId", order.getId());
            checkoutData.put("totalAmount", order.getTotalAmount());
            checkoutData.put("paymentStatus", order.getPaymentStatus().name());
            checkoutData.put("paymentMethod", order.getPaymentMethod().name());
            checkoutData.put("orderStatus", order.getStatus().name());

            if (order.getPaymentMethod() == PaymentMethod.SEPAY && order.getStatus() == OrderStatus.PENDING) {
                // Tương tự, update QR des
                String qrUrl = String.format("https://qr.sepay.vn/img?bank=VietinBank&acc=109874753814&template=compact&amount=%d&des=SEVQR+TKPCCT+DH%d",
                        order.getTotalAmount().longValue(), order.getId());

                checkoutData.put("qrCodeUrl", qrUrl);
                checkoutData.put("bankInfo", Map.of(
                        "bankName", "VietinBank",
                        "accountNumber", "109874753814",
                        "accountHolder", "CAO CHIEN THANG",
                        "transferContent", "SEVQR TKPCCT DH" + order.getId()
                ));
            } else if (order.getPaymentMethod() == PaymentMethod.COD) {
                checkoutData.put("message", "Đơn hàng COD đã được đặt. Vui lòng chờ giao hàng và thanh toán khi nhận hàng.");
            } else if (order.getPaymentStatus() == PaymentStatus.PAID) {
                checkoutData.put("message", "Đơn hàng đã được thanh toán thành công.");
            }

            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                    .message("Thông tin thanh toán")
                    .data(checkoutData)
                    .status(HttpStatus.OK.value())
                    .build());
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.<Map<String, Object>>builder()
                            .message(e.getMessage())
                            .status(HttpStatus.NOT_FOUND.value())
                            .build());
        }
    }

    // Fixed: Use path variable orderId directly (no body needed for AJAX check, matching demo)
    @PostMapping("/{orderId}/check-payment-status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, String>>> checkPaymentStatus(@PathVariable Long orderId) {
        User currentUser = userService.getCurrentLoggedInUser();
        Optional<Order> orderOpt = orderRepository.findByIdAndUserId(orderId, currentUser.getId());
        if (orderOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.<Map<String, String>>builder()
                            .message("Không tìm thấy đơn hàng: " + orderId)
                            .status(HttpStatus.NOT_FOUND.value())
                            .build());
        }

        Order order = orderOpt.get();
        return ResponseEntity.ok(ApiResponse.<Map<String, String>>builder()
                .message("Success")
                .data(Map.of("paymentStatus", order.getPaymentStatus().name()))
                .status(HttpStatus.OK.value())
                .build());
    }

    @PostMapping("/sepay-webhook")
    public ResponseEntity<ApiResponse<String>> handleSepayWebhook(@RequestBody SepayWebhookRequest webhook) {
        try {
            log.info("Webhook nhận từ SEPAY: {}", webhook);
            orderService.handleSepayWebhook(webhook);
            return ResponseEntity.ok(ApiResponse.<String>builder()
                    .status(HttpStatus.OK.value())
                    .message("Webhook processed")
                    .build());
        } catch (Exception e) {
            log.warn("Webhook processed with warning: {}", e.getMessage());  // Always 200 for idempotency
            return ResponseEntity.ok(ApiResponse.<String>builder()
                    .status(HttpStatus.OK.value())
                    .message("Webhook received")
                    .build());
        }
    }




    // GET /api/orders/user/{userId}
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getOrdersByUser(@PathVariable Long userId) {
        List<OrderResponse> orders = orderService.getOrdersByUser(userId);
        return ResponseEntity.ok(
                ApiResponse.success("Lấy danh sách đơn hàng theo id người dùng thành công", orders)
        );
    }

    @GetMapping("/unique-emails")
    @PreAuthorize("hasRole('ADMIN')") // Chỉ admin
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getUniqueUserEmails() {
        List<Map<String, String>> emails = orderService.getUniqueUserEmails();
        return ResponseEntity.ok(ApiResponse.<List<Map<String, String>>>builder()
                .message("Unique emails fetched successfully")
                .data(emails)
                .status(HttpStatus.OK.value())
                .build());
    }



    @GetMapping("/best-sellers")
    public ResponseEntity<ApiResponse<List<ProductCardDto>>> getBestSellers(
            @RequestParam(defaultValue = "12") Integer limit) {

        List<ProductCardDto> bestSellers = orderService.getBestSellingProductsLast30Days(limit);

        return ResponseEntity.ok(ApiResponse.success("Lấy sản phẩm bán chạy thành công", bestSellers));
    }
    @GetMapping("/admin/export/excel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportOrdersToExcel(
            @RequestParam(required = false) Double minTotalAmount,
            @RequestParam(required = false) Double maxTotalAmount,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) Integer provinceCode,
            @RequestParam(required = false) Integer wardCode,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String emails,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String startDate,   // ← chấp nhận: 2025-04-01 HOẶC 2025-04-01 10:30
            @RequestParam(required = false) String endDate) {

        // ==================== FIX NGÀY THÁNG 100% =====================
        LocalDateTime start = null;
        LocalDateTime end = null;

        // 2 format phổ biến nhất
        DateTimeFormatter dateOnly = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter dateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        if (StringUtils.hasText(startDate)) {
            String s = startDate.trim();
            try {
                if (s.length() == 10) { // chỉ có ngày → 2025-04-01
                    LocalDate ld = LocalDate.parse(s, dateOnly);
                    start = ld.atStartOfDay(); // 00:00:00
                } else { // có giờ phút
                    LocalDateTime ldt = LocalDateTime.parse(s.length() > 16 ? s.substring(0, 16) : s, dateTime);
                    start = ldt.withHour(0).withMinute(0).withSecond(0).withNano(0);
                }
            } catch (Exception e) {
                log.warn("Cannot parse startDate: '{}', ignored", startDate);
            }
        }

        if (StringUtils.hasText(endDate)) {
            String s = endDate.trim();
            try {
                if (s.length() == 10) { // chỉ có ngày → 2025-04-05
                    LocalDate ld = LocalDate.parse(s, dateOnly);
                    end = ld.atTime(23, 59, 59, 999999999); // cuối ngày
                } else {
                    LocalDateTime ldt = LocalDateTime.parse(s.length() > 16 ? s.substring(0, 16) : s, dateTime);
                    end = ldt.withHour(23).withMinute(59).withSecond(59).withNano(999999999);
                }
            } catch (Exception e) {
                log.warn("Cannot parse endDate: '{}', ignored", endDate);
            }
        }
        // ===========================================================

        OrderSearchRequest request = OrderSearchRequest.builder()
                .minTotalAmount(minTotalAmount)
                .maxTotalAmount(maxTotalAmount)
                .provinceCode(provinceCode)
                .wardCode(wardCode)
                .search(search)
                .productId(productId)
                .startDate(start)
                .endDate(end)
                .build();

        // Status
        if (StringUtils.hasText(status)) {
            request.setStatus(Arrays.stream(status.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .map(OrderStatus::valueOf)
                    .toList());
        }

        // Payment method
        if (StringUtils.hasText(paymentMethod)) {
            request.setPaymentMethod(Arrays.stream(paymentMethod.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .map(PaymentMethod::valueOf)
                    .toList());
        }

        // Emails
        if (StringUtils.hasText(emails)) {
            request.setEmails(Arrays.stream(emails.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList());
        }

        try {
            byte[] excel = orderService.exportOrdersToExcel(request);

            String fileName = "orders_export_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", fileName);
            headers.setCacheControl("no-cache");

            return new ResponseEntity<>(excel, headers, HttpStatus.OK);

        } catch (Exception e) {
            log.error("Export Excel failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    }


// Separate Controller for view rendering
//@Controller
//@RequestMapping("/api/orders")
//@RequiredArgsConstructor
//class CheckoutViewController {
//    private final OrderRepository orderRepository;
//    private final UserService userService;
//    private final OrderService orderService;
//
//    @GetMapping("/{orderId}/checkout-page")
//    @PreAuthorize("isAuthenticated()")
//    public String getCheckoutPage(@PathVariable Long orderId, Model model) {
//        Order order = orderRepository.findByIdAndUserId(orderId, userService.getCurrentLoggedInUser().getId())
//                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng: " + orderId));
//        OrderResponse orderResponse = OrderResponse.fromEntity(order);
//
//        if (order.getPaymentMethod() == PaymentMethod.SEPAY && order.getStatus() == OrderStatus.WAITING_FOR_PAYMENT) {
//            String qrUrl = String.format("https://qr.sepay.vn/img?bank=VietinBank&acc=109874753814&template=compact&amount=%d&des=DH%d",
//                    order.getTotalAmount().longValue(), order.getId());
//            orderResponse.setQrCodeUrl(qrUrl);
//            Map<String, Object> bankInfo = Map.of(
//                    "bankName", "VietinBank",
//                    "accountNumber", "109874753814",
//                    "accountHolder", "Cao Chiến Thắng",
//                    "transferContent", "DH" + order.getId()
//            );
//            orderResponse.setBankInfo(bankInfo);
//        }
//
//        model.addAttribute("order", orderResponse);
//        return "checkout"; // Assume you have src/main/resources/templates/checkout.html
//    }
//}