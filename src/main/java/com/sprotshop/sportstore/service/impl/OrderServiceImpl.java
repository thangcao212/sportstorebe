package com.sprotshop.sportstore.service.impl;

import com.sprotshop.sportstore.Enum.OrderStatus;
import com.sprotshop.sportstore.Enum.PaymentMethod;
import com.sprotshop.sportstore.Enum.PaymentStatus;
import com.sprotshop.sportstore.entity.*;
import com.sprotshop.sportstore.exception.*;
import com.sprotshop.sportstore.repository.*;
import com.sprotshop.sportstore.request.CreateOrderRequest;
import com.sprotshop.sportstore.request.OrderSearchRequest;
import com.sprotshop.sportstore.request.SepayWebhookRequest;
import com.sprotshop.sportstore.response.OrderResponse;
import com.sprotshop.sportstore.response.PageResponse;
import com.sprotshop.sportstore.service.CartService;
import com.sprotshop.sportstore.service.OrderService;
import com.sprotshop.sportstore.service.TransactionService;
import com.sprotshop.sportstore.service.UserService;
import com.sprotshop.sportstore.utils.OrderSpecification;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserService userService;
    private final CartService cartService;
    private final ProvinceRepository provinceRepository;
    private final DistrictRepository districtRepository;
    private final WardRepository wardRepository;
    private final AddressRepository addressRepository;
    private final ProductSizeRepository productSizeRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final UserRepository userRepository;

    private static final long WAITING_TIMEOUT_MINUTES = 5;  // 5 minutes for testing SEPAY
    private static final long COMPLETE_AFTER_DAYS = 7;

    @Override
    @Transactional
    @CacheEvict(value = {"userOrders", "allOrders"}, key = "#userService.getCurrentLoggedInUser().id + '-*'", allEntries = true)
    public OrderResponse createOrderFromCart(CreateOrderRequest request) {
        try{
            User currentUser = userService.getCurrentLoggedInUser();
            Long userId = currentUser.getId();
            log.info("Creating order for userId: {}", userId);

            Cart cart = cartRepository.findByUserId(userId)
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy giỏ hàng cho user ID: " + userId));

            if (CollectionUtils.isEmpty(cart.getCartItems())) {
                throw new IllegalStateException("Giỏ hàng trống");
            }

            Map<String, Integer> productSizeQuantityMap = new HashMap<>();
            Map<String, BigDecimal> priceAtOrderMap = new HashMap<>();
            BigDecimal totalAmount = BigDecimal.ZERO;
            Map<String, Integer> stockUpdates = new HashMap<>();

            for (CartItem cartItem : cart.getCartItems()) {
                try {
                    Product product = cartItem.getProduct();
                    String size = cartItem.getSize();
                    int quantity = cartItem.getQuantity();
                    String productSizeKey = product.getId() + "-" + size;

                    ProductSize variant = productSizeRepository.findByProductIdAndSize(product.getId(), size)
                            .orElseThrow(() -> new NotFoundException("Không tìm thấy kích thước sản phẩm: " + product.getId() + ", size: " + size));
                    if (variant.getStockQuantity() < quantity) {
                        throw new IllegalStateException("Sản phẩm hết hàng cho kích thước " + size + ": " + product.getName());
                    }

                    // 👈 Fix: Direct assignment since price is already BigDecimal
                    BigDecimal price = product.getPrice();
                    productSizeQuantityMap.put(productSizeKey, quantity);
                    priceAtOrderMap.put(productSizeKey, price);
                    totalAmount = totalAmount.add(price.multiply(BigDecimal.valueOf(quantity)));
                    stockUpdates.put(productSizeKey, quantity);
                } catch (NotFoundException | IllegalStateException e) {
                    log.warn("Stock check failed for cartItem {}: {}", cartItem.getId(), e.getMessage());
                    throw e;  // Fail fast
                }
            }

            // 👈 Xử lý Address: Ưu tiên dùng existing nếu có addressId, fallback tạo mới và link với user
            Address address;
            if (request.getAddressId() != null) {
                // Tìm existing address của user
                address = addressRepository.findByIdAndUserId(request.getAddressId(), userId)
                        .orElseThrow(() -> new NotFoundException("Địa chỉ không tồn tại hoặc không thuộc user: " + request.getAddressId()));
                log.info("Used existing address ID: {} for order", request.getAddressId());
            } else {
                // Tạo mới: Validate codes, build fullAddress, link user
                Province province = provinceRepository.findById(request.getProvinceCode())
                        .orElseThrow(() -> new NotFoundException("Mã tỉnh không hợp lệ: " + request.getProvinceCode()));
                District district = districtRepository.findById(request.getDistrictCode())
                        .orElseThrow(() -> new NotFoundException("Mã huyện không hợp lệ: " + request.getDistrictCode()));
                Ward ward = wardRepository.findById(request.getWardCode())
                        .orElseThrow(() -> new NotFoundException("Mã xã không hợp lệ: " + request.getWardCode()));

                String fullAddress = String.format("%s, %s, %s, %s", request.getStreet(), ward.getName(), district.getName(), province.getName());
                address = Address.builder()
                        .provinceCode(request.getProvinceCode())
                        .districtCode(request.getDistrictCode())
                        .wardCode(request.getWardCode())
                        .street(request.getStreet())
                        .fullAddress(fullAddress)
                        .user(currentUser)  // 👈 Link với user
                        .build();
                addressRepository.save(address);
                currentUser.addAddress(address);  // 👈 Thêm vào list (nếu có method addAddress trong User)
                userRepository.save(currentUser);  // Sync list
                log.info("Created and linked new address for user: {}", userId);
            }

            // 👈 NEW: Snapshot full address lúc tạo (immutable cho lịch sử, không ảnh hưởng logic khác)
            String snapshotAddress = address.getFullAddress();

            // Gán trạng thái dựa trên paymentMethod
            // Set initial status based on flow
            OrderStatus initialStatus = (request.getPaymentMethod() == PaymentMethod.SEPAY)
                    ? OrderStatus.WAITING_FOR_PAYMENT : OrderStatus.PENDING;
            PaymentStatus initialPaymentStatus = PaymentStatus.PENDING;

            Order order = Order.builder()
                    .user(currentUser)
                    .address(address)
                    .deliveryAddress(snapshotAddress)  // 👈 Set snapshot (no logic change)
                    .totalAmount(totalAmount)
                    .status(initialStatus)
                    .orderStatus(initialStatus)  // 👈 Set orderStatus same as status (as per original logic, adjust if needed)
                    .paymentMethod(request.getPaymentMethod())
                    .paymentStatus(initialPaymentStatus)

                    .shippingRecipientName(request.getRecipientName())
                    .shippingPhone(request.getPhone())
                    .notes(request.getNotes())
                    .orderItems(new ArrayList<>())
                    .build();
            Order savedOrder = orderRepository.save(order);
            try {
                List<OrderItem> orderItems = new ArrayList<>();
                for (Map.Entry<String, Integer> entry : productSizeQuantityMap.entrySet()) {
                    String[] parts = entry.getKey().split("-");
                    Long productId = Long.parseLong(parts[0]);
                    String size = parts[1];
                    Product product = productRepository.findById(productId)
                            .orElseThrow(() -> new NotFoundException("Không tìm thấy sản phẩm: " + productId));

                    OrderItem item = OrderItem.builder()
                            .order(savedOrder)
                            .product(product)
                            .quantity(entry.getValue())
                            .price(priceAtOrderMap.get(entry.getKey()))
                            .size(size)
                            .build();
                    orderItems.add(item);
                }
                orderItemRepository.saveAll(orderItems);
                savedOrder.setOrderItems(orderItems);

                stockUpdates.forEach((productSizeKey, quantity) -> {
                    String[] parts = productSizeKey.split("-");
                    Long productId = Long.parseLong(parts[0]);
                    String size = parts[1];
                    ProductSize variant = productSizeRepository.findByProductIdAndSize(productId, size)
                            .orElseThrow(() -> new NotFoundException("Không tìm thấy kích thước sản phẩm: " + productId + ", size: " + size));
                    variant.setStockQuantity(variant.getStockQuantity() - quantity);
                    productSizeRepository.save(variant);
                });

                cartService.clearCart();
            } catch (Exception e) {
                log.error("Failed to save items/stock for order {}: {}", savedOrder.getId(), e.getMessage());
                throw new RuntimeException("Lỗi lưu chi tiết đơn hàng: " + e.getMessage(), e);
            }
            Hibernate.initialize(savedOrder.getOrderItems());

            OrderResponse response = OrderResponse.fromEntity(orderRepository.findById(savedOrder.getId()).orElseThrow());
            if (request.getPaymentMethod() == PaymentMethod.SEPAY) {
                // Thay des=DH%d bằng des=SEVQR+TKPCCT+DH%d
                String qrUrl = String.format("https://qr.sepay.vn/img?bank=VietinBank&acc=109874753814&template=compact&amount=%d&des=SEVQR+TKPCCT+DH%d",
                        savedOrder.getTotalAmount().longValue(), savedOrder.getId());
                response.setQrCodeUrl(qrUrl);
                response.setBankInfo(Map.of(
                        "bankName", "VietinBank",
                        "accountNumber", "109874753814",
                        "accountHolder", "CAO CHIEN THANG",
                        "transferContent", "SEVQR TKPCCT DH" + savedOrder.getId()  // Update content hướng dẫn
                ));
            }
            return response;
        }catch (Exception e) {
            log.error("Create order failed: {}", e.getMessage(), e);
            throw e;  // Propagate to controller
        }
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderDetails(Long orderId) {
        User currentUser = userService.getCurrentLoggedInUser();
        Order order = orderRepository.findByIdAndUserId(orderId, currentUser.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng: " + orderId));
        Hibernate.initialize(order.getOrderItems());

        // 👈 NEW: For immutable display, OrderResponse should prioritize deliveryAddress over address.fullAddress
        // (Assume OrderResponse.fromEntity handles this, e.g., if fullAddress is from deliveryAddress)
        return OrderResponse.fromEntity(order);
    }


    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderByIdForAdmin(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng: " + orderId));
        Hibernate.initialize(order.getOrderItems());
        return OrderResponse.fromEntity(order);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "userOrders", keyGenerator = "userOrderKeyGenerator")
    public PageResponse<OrderResponse> getUserOrders(Pageable pageable) {
        User currentUser = userService.getCurrentLoggedInUser();
        Page<Order> orders = orderRepository.findByUserId(currentUser.getId(), pageable);
        orders.forEach(order -> Hibernate.initialize(order.getOrderItems()));
        return PageResponse.fromPage(orders.map(OrderResponse::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "allOrders", keyGenerator = "allOrderKeyGenerator")
    public PageResponse<OrderResponse> getAllOrders(Pageable pageable) {
        Page<Order> orders = orderRepository.findAll(pageable);
        orders.forEach(order -> Hibernate.initialize(order.getOrderItems()));
        return PageResponse.fromPage(orders.map(OrderResponse::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> searchOrders(OrderSearchRequest request, Pageable pageable) {
        log.info("Tìm kiếm đơn hàng với request: {}", request);
        Specification<Order> spec = OrderSpecification.filterOrders(request);
        Page<Order> orders = orderRepository.findAll(spec, pageable);
        log.info("Tìm thấy {} đơn hàng", orders.getTotalElements());
        orders.forEach(order -> Hibernate.initialize(order.getOrderItems()));
        return PageResponse.fromPage(orders.map(OrderResponse::fromEntity));
    }


    // Validation helper (unchanged)
    private void validateTransition(Order order, OrderStatus newStatus, PaymentStatus newPaymentStatus) {
        try {
            if (newStatus != null) {
                order.getStatus().canTransitionTo(newStatus, order.getPaymentMethod(), order.getPaymentStatus());
            }
            if (newPaymentStatus != null) {
                if (!isValidPaymentTransition(order.getPaymentStatus(), newPaymentStatus)) {
                    throw new InvalidOrderTransitionException("Invalid payment transition to " + newPaymentStatus);
                }
            }
        } catch (InvalidOrderTransitionException e) {
            log.warn("Flow violation for order {}: {}", order.getId(), e.getMessage());
            throw e;
        }
    }

    private boolean isValidPaymentTransition(PaymentStatus current, PaymentStatus next) {
        boolean valid = switch (next) {
            case PAID -> current == PaymentStatus.PENDING;
            case CANCELLED -> current == PaymentStatus.PENDING || current == PaymentStatus.PAID;
            case REFUNDED -> current == PaymentStatus.PAID;
            default -> false;
        };
        if (!valid) {
            throw new InvalidOrderTransitionException("Invalid payment transition from " + current + " to " + next);
        }
        return valid;
    }

    // 3. UPDATE STATUS (Generic, uses validation) - FIXED: Auto-confirm COD payment khi set DELIVERED
    @Override
    @Transactional
    @CacheEvict(value = {"userOrders", "allOrders"}, allEntries = true)
    public OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus) {
        try {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng: " + orderId));

            // NEW: Auto-confirm COD payment khi set DELIVERED (nếu đang PENDING) - FIX cho flow COD
            if (order.getPaymentMethod() == PaymentMethod.COD &&
                    newStatus == OrderStatus.DELIVERED &&
                    order.getPaymentStatus() == PaymentStatus.PENDING) {
                order.setPaymentStatus(PaymentStatus.PAID);
                log.info("Auto-confirmed COD payment for order {} during status update to DELIVERED", orderId);
            }

            // EXISTING: Auto-confirm COD payment if transitioning DELIVERED -> COMPLETED and PENDING
            if (order.getStatus() == OrderStatus.DELIVERED && newStatus == OrderStatus.COMPLETED &&
                    order.getPaymentMethod() == PaymentMethod.COD && order.getPaymentStatus() == PaymentStatus.PENDING) {
                order.setPaymentStatus(PaymentStatus.PAID);
                log.info("Auto-confirmed COD payment for order {} during status update to COMPLETED", orderId);
            }

            // EXISTING: Auto-confirm SEPAY payment if transitioning WAITING_FOR_PAYMENT -> PROCESSING and PENDING
            if (order.getStatus() == OrderStatus.WAITING_FOR_PAYMENT && newStatus == OrderStatus.PROCESSING &&
                    order.getPaymentMethod() == PaymentMethod.SEPAY && order.getPaymentStatus() == PaymentStatus.PENDING) {
                order.setPaymentStatus(PaymentStatus.PAID);
                log.info("Auto-confirmed SEPAY payment for order {} during status update to PROCESSING", orderId);
            }

            validateTransition(order, newStatus, null);
            order.setStatus(newStatus);
            order.setOrderStatus(newStatus);  // 👈 Sync orderStatus with status (as per original logic, adjust if needed)
            Hibernate.initialize(order.getOrderItems());
            return OrderResponse.fromEntity(orderRepository.save(order));
        } catch (InvalidOrderTransitionException e) {
            log.error("Transition error for order {}: {}", orderId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Update status failed for order {}: {}", orderId, e.getMessage());
            throw new RuntimeException("Lỗi cập nhật trạng thái: " + e.getMessage(), e);
        }
    }

    // 4. CONFIRM PROCESSING (COD flow step 2)
    // COD: Confirm processing (PENDING -> PROCESSING)
    @Transactional
    public OrderResponse confirmProcessing(Long orderId) {
        try {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng: " + orderId));
            if (order.getPaymentMethod() != PaymentMethod.COD || order.getStatus() != OrderStatus.PENDING) {
                throw new InvalidOrderTransitionException("Chỉ xác nhận xử lý cho COD ở PENDING");
            }
            validateTransition(order, OrderStatus.PROCESSING, null);
            order.setStatus(OrderStatus.PROCESSING);
            order.setOrderStatus(OrderStatus.PROCESSING);  // 👈 Sync
            return OrderResponse.fromEntity(orderRepository.save(order));
        } catch (Exception e) {
            log.error("Confirm processing failed for order {}: {}", orderId, e.getMessage());
            throw e;
        }
    }

    @Transactional
    public OrderResponse confirmCodPayment(Long orderId) {
        try {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng: " + orderId));
            if (order.getPaymentMethod() != PaymentMethod.COD || order.getStatus() != OrderStatus.DELIVERED ||
                    order.getPaymentStatus() != PaymentStatus.PENDING) {
                throw new InvalidOrderTransitionException("Chỉ xác nhận COD payment khi DELIVERED và PENDING");
            }

            // Set PAID first
            order.setPaymentStatus(PaymentStatus.PAID);

            // Validate and set COMPLETED
            validateTransition(order, OrderStatus.COMPLETED, null);
            order.setStatus(OrderStatus.COMPLETED);
            order.setOrderStatus(OrderStatus.COMPLETED);  // 👈 Sync
            Hibernate.initialize(order.getOrderItems());
            return OrderResponse.fromEntity(orderRepository.save(order));
        } catch (Exception e) {
            log.error("COD confirm error for order {}: {}", orderId, e.getMessage());
            throw e;
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = {"userOrders", "allOrders"}, key = "#userService.getCurrentLoggedInUser().id + '-*'", allEntries = true)
    public OrderResponse cancelOrder(Long orderId) {
        try {
            User currentUser = userService.getCurrentLoggedInUser();
            Order order = orderRepository.findByIdAndUserId(orderId, currentUser.getId())
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng: " + orderId));

            // Allow cancel from PENDING/WAITING/PROCESSING/SHIPPED/DELIVERED (not COMPLETED)
            if (order.getStatus() == OrderStatus.COMPLETED) {
                throw new InvalidOrderTransitionException("Cannot cancel completed order");
            }
            validateTransition(order, OrderStatus.CANCELED, PaymentStatus.CANCELLED);

            restoreStock(order);
            order.setStatus(OrderStatus.CANCELED);
            order.setOrderStatus(OrderStatus.CANCELED);  // 👈 Sync
            order.setPaymentStatus(PaymentStatus.CANCELLED);
            return OrderResponse.fromEntity(orderRepository.save(order));
        } catch (InvalidOrderTransitionException e) {
            log.error("Cancel flow violation for order {}: {}", orderId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Cancel failed for order {}: {}", orderId, e.getMessage());
            throw e;
        }
    }

    @Override
    @Transactional
    public void handleSepayWebhook(SepayWebhookRequest webhook) {
        try {
            log.info("Processing webhook: id={}, amount={}, type={}", webhook.getId(), webhook.getTransferAmount(), webhook.getTransferType());

            if (!"in".equalsIgnoreCase(webhook.getTransferType())) {
                log.info("Ignoring non-incoming transfer");
                return;
            }

            Transaction tx;
            try {
                tx = transactionService.saveFromWebhook(webhook);
            } catch (IllegalStateException e) {
                log.warn("Duplicate tx ignored: {}", e.getMessage());
                return;
            }

            String content = webhook.getContent() != null ? webhook.getContent().trim() : "";
            if (content.isBlank()) {
                log.warn("Empty content, skipping");
                return;
            }

            Pattern pattern = Pattern.compile("\\bDH\\s*(\\d+)\\b", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(content);
            if (!matcher.find()) {
                log.warn("No DH code in content: '{}'", content);
                return;
            }

            Long orderId;
            try {
                orderId = Long.valueOf(matcher.group(1));
            } catch (NumberFormatException e) {
                log.error("Parse error for content '{}': {}", content, e.getMessage());
                return;
            }

            Optional<Order> optionalOrder = orderRepository.findByIdAndTotalAmountAndPaymentStatus(
                    orderId, webhook.getTransferAmount(), PaymentStatus.PENDING);
            if (optionalOrder.isEmpty()) {
                log.warn("No matching order: id={}, amount={}", orderId, webhook.getTransferAmount());
                return;
            }

            Order order = optionalOrder.get();
            if (order.getPaymentStatus() == PaymentStatus.PAID) {
                log.info("Order {} already PAID", orderId);
                return;
            }

            // FIX: Set PAID first
            order.setPaymentStatus(PaymentStatus.PAID);

            // Then validate PROCESSING (uses new PAID)
            validateTransition(order, OrderStatus.PROCESSING, null);
            order.setStatus(OrderStatus.PROCESSING);
            order.setOrderStatus(OrderStatus.PROCESSING);  // 👈 Sync
            orderRepository.save(order);
            log.info("SUCCESS: Updated order {} to PAID/PROCESSING via webhook", orderId);
        } catch (InvalidOrderTransitionException e) {
            log.warn("Webhook flow violation: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Webhook error: {}", e.getMessage(), e);
            throw e;
        }
    }

    // 10. HELPER: Restore Stock (Safe with error handling)
    private void restoreStock(Order order) {
        try {
            if (!CollectionUtils.isEmpty(order.getOrderItems())) {
                List<ProductSize> variantsToUpdate = new ArrayList<>();
                for (OrderItem item : order.getOrderItems()) {
                    ProductSize variant = productSizeRepository.findByProductIdAndSize(
                                    item.getProduct().getId(), item.getSize())
                            .orElseThrow(() -> new NotFoundException("Không tìm thấy kích thước sản phẩm"));
                    variant.setStockQuantity(variant.getStockQuantity() + item.getQuantity());
                    variantsToUpdate.add(variant);
                }
                productSizeRepository.saveAll(variantsToUpdate);
                log.info("Restored stock for canceled order: {}", order.getId());
            }
        } catch (Exception e) {
            log.error("Failed to restore stock for order {}: {}", order.getId(), e.getMessage());
            throw new RuntimeException("Lỗi khôi phục tồn kho: " + e.getMessage(), e);
        }
    }

    // Existing: addTrackingNumber (add validate if needed: e.g., from PROCESSING -> SHIPPED)
    @Override
    @Transactional
    @CacheEvict(value = {"userOrders", "allOrders"}, allEntries = true)
    public OrderResponse addTrackingNumber(Long orderId, String trackingNumber) {
        try {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng: " + orderId));
            if (!StringUtils.hasText(trackingNumber)) {
                throw new IllegalArgumentException("Tracking number required");
            }
            order.setTrackingNumber(trackingNumber);
            if (order.getStatus() == OrderStatus.PROCESSING) {
                validateTransition(order, OrderStatus.SHIPPED, null);
                order.setStatus(OrderStatus.SHIPPED);
                order.setOrderStatus(OrderStatus.SHIPPED);  // 👈 Sync
            }
            Hibernate.initialize(order.getOrderItems());
            return OrderResponse.fromEntity(orderRepository.save(order));
        } catch (Exception e) {
            log.error("Add tracking failed for order {}: {}", orderId, e.getMessage());
            throw e;
        }
    }

    @Override
    public List<OrderResponse> getOrdersByUser(Long userId) {
        List<Order> orders = orderRepository.findByUserId(userId);
        return orders.stream()
                .map(OrderResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, String>> getUniqueUserEmails() {
        try {
            List<String> emails = orderRepository.findUniqueUserEmails(); // Gọi repo
            return emails.stream()
                    .map(email -> Map.of("value", email, "label", email)) // Format cho Select
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch unique emails: {}", e.getMessage());
            return List.of(); // Trả empty nếu lỗi
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderStatus> getPossibleNextStatuses(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng: " + orderId));
        return order.getStatus().getPossibleNextStates(order.getPaymentMethod(), order.getPaymentStatus());
    }

    // ENHANCED: Scheduler for SEPAY timeout - Added detailed logging for stuck test orders
    @Scheduled(fixedRate = 300000)  // Every 5 minutes
    @Transactional
    public void timeoutWaitingOrders() {
        try {
            LocalDateTime threshold = LocalDateTime.now().minusMinutes(WAITING_TIMEOUT_MINUTES);
            // Filter only SEPAY orders in WAITING_FOR_PAYMENT with PENDING payment
            List<Order> waitingOrders = orderRepository.findByStatusAndCreatedAtBeforeAndPaymentMethodAndPaymentStatus(
                    OrderStatus.WAITING_FOR_PAYMENT, threshold, PaymentMethod.SEPAY, PaymentStatus.PENDING);

            if (waitingOrders.isEmpty()) {
                log.debug("No stuck SEPAY orders found for timeout.");
                return;
            }

            log.info("Found {} stuck SEPAY orders (WAITING_FOR_PAYMENT > {} min) for auto-cancel.", waitingOrders.size(), WAITING_TIMEOUT_MINUTES);

            for (Order order : waitingOrders) {
                try {
                    // Validate transition (should always pass for this case)
                    order.getStatus().canTransitionTo(OrderStatus.CANCELED, order.getPaymentMethod(), order.getPaymentStatus());

                    // Set statuses
                    order.setStatus(OrderStatus.CANCELED);
                    order.setOrderStatus(OrderStatus.CANCELED);  // 👈 Sync
                    order.setPaymentStatus(PaymentStatus.CANCELLED);

                    // Restore stock
                    restoreStock(order);

                    orderRepository.save(order);

                    log.warn("Auto-canceled stuck SEPAY order {} (created at {}): Timeout after {} minutes. Restored stock.",
                            order.getId(), order.getCreatedAt(), WAITING_TIMEOUT_MINUTES);
                } catch (Exception e) {
                    log.error("Failed to auto-cancel stuck SEPAY order {}: {}", order.getId(), e.getMessage(), e);
                    // Continue with next order
                }
            }

            log.info("Processed {} stuck SEPAY orders. Check logs for details.", waitingOrders.size());
        } catch (Exception e) {
            log.error("Scheduler timeoutWaitingOrders failed: {}", e.getMessage(), e);
            // No throw - keep app running
        }
    }

    // ENHANCED: Auto-complete scheduler - Also log for old DELIVERED orders
    @Scheduled(cron = "0 0 0 * * ?")  // Daily midnight
    @Transactional
    public void completeOldDeliveredOrders() {
        try {
            LocalDateTime threshold = LocalDateTime.now().minusDays(COMPLETE_AFTER_DAYS);
            List<Order> deliveredOrders = orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.DELIVERED, threshold);

            if (deliveredOrders.isEmpty()) {
                log.debug("No old DELIVERED orders for auto-complete.");
                return;
            }

            log.info("Found {} old DELIVERED orders (> {} days) for auto-complete.", deliveredOrders.size(), COMPLETE_AFTER_DAYS);

            for (Order order : deliveredOrders) {
                try {
                    if (order.getStatus().canTransitionTo(OrderStatus.COMPLETED, order.getPaymentMethod(), order.getPaymentStatus())) {
                        order.setStatus(OrderStatus.COMPLETED);
                        order.setOrderStatus(OrderStatus.COMPLETED);  // 👈 Sync
                        orderRepository.save(order);
                        log.info("Auto-completed old DELIVERED order: {}", order.getId());
                    } else {
                        log.warn("Skipped auto-complete for order {}: Invalid transition (check payment status).", order.getId());
                    }
                } catch (Exception e) {
                    log.error("Failed to auto-complete order {}: {}", order.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Scheduler completeOldDeliveredOrders failed: {}", e.getMessage(), e);
        }
    }
}