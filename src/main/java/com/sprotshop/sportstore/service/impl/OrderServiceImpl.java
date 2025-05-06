package com.sprotshop.sportstore.service.impl; // Thay đổi package nếu cần

import com.sprotshop.sportstore.Enum.OrderStatus;
import com.sprotshop.sportstore.Enum.PaymentMethod; // Import nếu cần dùng ở đây
import com.sprotshop.sportstore.entity.*;
import com.sprotshop.sportstore.exception.NotFoundException;
import com.sprotshop.sportstore.repository.*;
import com.sprotshop.sportstore.request.CreateOrderRequest;
import com.sprotshop.sportstore.response.OrderResponse;
import com.sprotshop.sportstore.service.CartService;
import com.sprotshop.sportstore.service.OrderService;
import com.sprotshop.sportstore.service.UserService; // Import UserService
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation; // Import Isolation nếu cần
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository; // Cần để lưu OrderItem
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserService userService;
    private  final CartService cartService;

    @Override
    @Transactional
    public OrderResponse createOrderFromCart(CreateOrderRequest request) {
        User currentUser = userService.getCurrentLoggedInUser();
        Long userId = currentUser.getId();
        log.info("Processing createOrderFromCart for userId: {}", userId);

        // 1. Lấy giỏ hàng (Repo đã có @EntityGraph)
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Cart not found for user ID: " + userId));

        if (CollectionUtils.isEmpty(cart.getCartItems())) {
            throw new IllegalStateException("Your cart is empty.");
        }
        log.debug("Cart found: id={}, items={}", cart.getId(), cart.getCartItems().size());

        // 2. Kiểm tra tồn kho, khóa giá... (Giữ nguyên logic)
        Map<Product, Integer> productQuantityMap = new HashMap<>();
        Map<Product, BigDecimal> priceAtOrderMap = new HashMap<>();
        BigDecimal calculatedTotalAmount = BigDecimal.ZERO;
        Map<Long, Integer> productStockUpdates = new HashMap<>();

        log.debug("Checking stock and locking prices...");
        if (!Hibernate.isInitialized(cart.getCartItems())) Hibernate.initialize(cart.getCartItems());
        for (CartItem cartItem : cart.getCartItems()) {
            Product product = cartItem.getProduct();
            int quantity = cartItem.getQuantity();

            if (product == null) throw new IllegalStateException("Cart item references null product.");
            int currentStock = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
            if (currentStock < quantity) {
                throw new IllegalArgumentException("Product '" + product.getName() + "' is out of stock for the requested quantity ("+ quantity +"). Available: " + currentStock);
            }

            BigDecimal priceAtOrder = BigDecimal.valueOf(product.getPrice());
            productQuantityMap.put(product, quantity);
            priceAtOrderMap.put(product, priceAtOrder);
            calculatedTotalAmount = calculatedTotalAmount.add(priceAtOrder.multiply(BigDecimal.valueOf(quantity)));
            productStockUpdates.put(product.getId(), quantity);
        }
        log.info("Stock checked. Calculated total: {}", calculatedTotalAmount);

        // 3. Tạo và Lưu Order Shell (Giữ nguyên)
        Order orderShell = Order.builder()
                // ... (gán các trường user, shipping, totalAmount, status...) ...
                .user(currentUser)
                .shippingRecipientName(request.getShippingRecipientName())
                .shippingPhone(request.getShippingPhone())
                .shippingStreet(request.getShippingStreet())
                .shippingWard(request.getShippingWard())
                .shippingDistrict(request.getShippingDistrict())
                .shippingCity(request.getShippingCity())
                .totalAmount(calculatedTotalAmount)
                .status(OrderStatus.PENDING)
                .notes(request.getNotes())
                .paymentMethod(request.getPaymentMethod())
                .paymentStatus("PENDING")
                .build();
        Order savedOrder = orderRepository.save(orderShell);
        log.info("Order shell saved successfully: id={}", savedOrder.getId());

        // 4. Tạo và Lưu OrderItems (Giữ nguyên)
        List<OrderItem> createdOrderItems = new ArrayList<>();
        for (Map.Entry<Product, Integer> entry : productQuantityMap.entrySet()) {
            Product product = entry.getKey();
            Integer quantity = entry.getValue();
            BigDecimal price = priceAtOrderMap.get(product);

            OrderItem orderItem = OrderItem.builder()
                    .order(savedOrder)
                    .product(product)
                    .quantity(quantity)
                    .price(price)
                    .build();
            orderItemRepository.save(orderItem); // Lưu từng OrderItem
            createdOrderItems.add(orderItem);
            log.debug("Saved OrderItem for Product ID: {}, Quantity: {}", product.getId(), quantity);
        }

        // 5. Cập nhật tồn kho (Giữ nguyên)
        log.warn("Updating stock quantity for {} products...", productStockUpdates.size());
        productStockUpdates.forEach((productId, quantityToDeduct) -> {
            Product productToUpdate = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalStateException("Product " + productId + " disappeared!"));
            int currentStock = productToUpdate.getStockQuantity() != null ? productToUpdate.getStockQuantity() : 0;
            int newStock = currentStock - quantityToDeduct;
            if (newStock < 0) throw new IllegalStateException("Stock update error for product '" + productToUpdate.getName() + "'.");
            productToUpdate.setStockQuantity(newStock);
            productRepository.save(productToUpdate);
        });
        log.info("Stock update complete.");

        // 6. --- THAY ĐỔI Ở ĐÂY: Gọi CartService để xóa giỏ hàng ---
        log.warn("Calling cartService.clearCart() for current user (userId={})", userId);
        try {
            cartService.clearCart(); // Gọi hàm clearCart() của CartService
            // Hàm clearCart này sẽ tự lấy user hiện tại và xóa các cart items tương ứng
            log.info("Cart cleared successfully via CartService for userId: {}", userId);
        } catch (Exception e) {
            // Ghi log lỗi nếu việc xóa giỏ hàng gặp vấn đề, nhưng không nên làm rollback đơn hàng đã tạo
            log.error("Error occurred while clearing cart for userId {} after order creation (orderId={}): {}", userId, savedOrder.getId(), e.getMessage());
            // Cân nhắc: Có nên throw lỗi ở đây không hay chỉ log?
            // Nếu throw lỗi ở đây, toàn bộ transaction tạo order sẽ bị rollback -> không tốt.
            // Chỉ nên log lỗi và tiếp tục. Việc giỏ hàng chưa được xóa có thể xử lý sau.
        }
        // --- KẾT THÚC THAY ĐỔI ---

        // 7. Trả về response DTO (Load lại Order bằng ID để chắc chắn)
        Order finalOrder = orderRepository.findById(savedOrder.getId())
                .orElseThrow(() -> new IllegalStateException("Order just created not found! ID: " + savedOrder.getId()));
        log.info("Order created successfully with id={}", finalOrder.getId());
        return OrderResponse.fromEntity(finalOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getUserOrders(Pageable pageable) {
        User currentUser = userService.getCurrentLoggedInUser();
        log.debug("Fetching order history for userId: {}", currentUser.getId());
        return orderRepository.findByUserId(currentUser.getId(), pageable).map(OrderResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderDetails(Long orderId) {
        User currentUser = userService.getCurrentLoggedInUser();
        log.debug("Fetching order details for orderId: {}, userId: {}", orderId, currentUser.getId());
        Order order = orderRepository.findByIdAndUserId(orderId, currentUser.getId())
                .orElseThrow(() -> new NotFoundException("Order not found or does not belong to user. ID: " + orderId));
        return OrderResponse.fromEntity(order);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long orderId) {
        User currentUser = userService.getCurrentLoggedInUser();
        Long userId = currentUser.getId();
        log.warn("User userId={} requesting cancellation for orderId: {}", userId, orderId);

        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new NotFoundException("Order not found or does not belong to user. ID: " + orderId));

        // Kiểm tra trạng thái cho phép hủy
        List<OrderStatus> cancellableStatuses = List.of(OrderStatus.PENDING, OrderStatus.WAITING_FOR_PAYMENT, OrderStatus.PROCESSING);
        if (!cancellableStatuses.contains(order.getStatus())) {
            throw new IllegalStateException("Order cannot be canceled in its current state: " + order.getStatus());
        }

        // Hoàn trả tồn kho
        log.warn("Restoring stock for canceled orderId: {}", orderId);
        // Đảm bảo items và product được load (findByIdAndUserId đã có EntityGraph)
        if (!CollectionUtils.isEmpty(order.getOrderItems())) {
            for (OrderItem item : order.getOrderItems()) {
                Product product = item.getProduct();
                if (product != null) {
                    int quantityToRestore = item.getQuantity();
                    int currentStock = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
                    product.setStockQuantity(currentStock + quantityToRestore);
                    productRepository.save(product);
                    log.debug("Restored stock for productId={}: +{}", product.getId(), quantityToRestore);
                } else { log.error("OrderItem ID {} in canceled order links to null Product!", item.getId()); }
            }
            log.info("Stock restoration complete for orderId: {}", orderId);
        }

        order.setStatus(OrderStatus.CANCELED);
        Order canceledOrder = orderRepository.save(order);
        log.warn("Order id={} canceled successfully.", orderId);
        return OrderResponse.fromEntity(canceledOrder);
    }

    // --- Admin Functions ---
    @Override
    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus) {
        log.warn("[ADMIN] Updating status for orderId: {} to {}", orderId, newStatus);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));
        order.setStatus(newStatus);
        Order updatedOrder = orderRepository.save(order);
        return OrderResponse.fromEntity(updatedOrder);
    }

    @Override
    @Transactional
    public OrderResponse addTrackingNumber(Long orderId, String trackingNumber) {
        log.info("[ADMIN] Adding tracking '{}' to orderId: {}", trackingNumber, orderId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));
        order.setTrackingNumber(trackingNumber);
        if (order.getStatus() == OrderStatus.PROCESSING) {
            order.setStatus(OrderStatus.SHIPPED);
        }
        Order updatedOrder = orderRepository.save(order);
        return OrderResponse.fromEntity(updatedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderByIdForAdmin(Long orderId) {
        log.debug("[ADMIN] Fetching order details for orderId: {}", orderId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));
        return OrderResponse.fromEntity(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        log.debug("[ADMIN] Fetching all orders page: {}", pageable);
        return orderRepository.findAll(pageable).map(OrderResponse::fromEntity);
    }
}