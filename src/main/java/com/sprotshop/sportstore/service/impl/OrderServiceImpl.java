package com.sprotshop.sportstore.service.impl;

import com.sprotshop.sportstore.Enum.OrderStatus;
import com.sprotshop.sportstore.Enum.PaymentMethod;
import com.sprotshop.sportstore.Enum.PaymentStatus;
import com.sprotshop.sportstore.entity.*;
import com.sprotshop.sportstore.exception.*;
import com.sprotshop.sportstore.repository.*;
import com.sprotshop.sportstore.request.*;
import com.sprotshop.sportstore.response.OrderResponse;
import com.sprotshop.sportstore.response.PageResponse;
import com.sprotshop.sportstore.service.*;
import com.sprotshop.sportstore.utils.OrderSpecification;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import org.thymeleaf.context.Context;  // 👈 THÊM: Cho template

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final WardRepository wardRepository;
    private final AddressRepository addressRepository;
    private final ProductSizeRepository productSizeRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final UserRepository userRepository;
    private final CouponService couponService;  // NEW: For applyCoupon
    private final CouponRepository couponRepository;
    private final EmailService emailService;  // 👈 THÊM: Để gửi email thông báo

    private static final long WAITING_TIMEOUT_MINUTES = 5;  // 5 minutes for testing SEPAY
    private static final long COMPLETE_AFTER_DAYS = 7;

    private BigDecimal calculateShippingFee(Integer provinceCode) {
        // Assume provinceCode 1-10 = inner province (Hanoi/HCMC), others outer
        if (provinceCode != null && provinceCode >= 1 && provinceCode <= 10) {
            return new BigDecimal("20000");  // 20k inner
        } else {
            return new BigDecimal("30000");  // 30k outer
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = {"userOrders", "allOrders"}, key = "#userService.getCurrentLoggedInUser().id + '-*'", allEntries = true)
    public OrderResponse createOrderFromCart(CreateOrderRequest request) {
        try {
            User currentUser = userService.getCurrentLoggedInUser();
            Long userId = currentUser.getId();
            log.info("Creating order for userId: {}", userId);

            // === 1. Kiểm tra giỏ hàng ===
            Cart cart = cartRepository.findByUserId(userId)
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy giỏ hàng cho user ID: " + userId));

            if (CollectionUtils.isEmpty(cart.getCartItems())) {
                throw new IllegalStateException("Giỏ hàng trống");
            }

            // === 2. Tính tổng tiền sản phẩm + kiểm tra tồn kho ===
            Map<String, Integer> productSizeQuantityMap = new HashMap<>();
            Map<String, BigDecimal> priceAtOrderMap = new HashMap<>();
            BigDecimal itemsTotal = BigDecimal.ZERO;
            Map<String, Integer> stockUpdates = new HashMap<>();

            for (CartItem cartItem : cart.getCartItems()) {
                Product product = cartItem.getProduct();
                String size = cartItem.getSize();
                int quantity = cartItem.getQuantity();
                String key = product.getId() + "-" + size;

                ProductSize variant = productSizeRepository.findByProductIdAndSize(product.getId(), size)
                        .orElseThrow(() -> new NotFoundException("Không tìm thấy size: " + size + " cho sản phẩm ID: " + product.getId()));

                if (variant.getStockQuantity() < quantity) {
                    throw new IllegalStateException("Hết hàng: " + product.getName() + " (size " + size + ")");
                }

                BigDecimal price = product.getPrice();
                productSizeQuantityMap.put(key, quantity);
                priceAtOrderMap.put(key, price);
                itemsTotal = itemsTotal.add(price.multiply(BigDecimal.valueOf(quantity)));
                stockUpdates.put(key, quantity);
            }

            // === 3. Xử lý địa chỉ giao hàng ===
            Address address;
            Integer provinceCodeForShip;
            if (request.getAddressId() != null) {
                address = addressRepository.findByIdAndUserId(request.getAddressId(), userId)
                        .orElseThrow(() -> new NotFoundException("Địa chỉ không tồn tại hoặc không thuộc bạn"));
                provinceCodeForShip = address.getProvinceCode();
            } else {
                Province province = provinceRepository.findById(request.getProvinceCode())
                        .orElseThrow(() -> new NotFoundException("Mã tỉnh không hợp lệ"));
                Ward ward = wardRepository.findById(request.getWardCode())
                        .orElseThrow(() -> new NotFoundException("Mã xã không hợp lệ"));

                String fullAddress = String.format("%s, %s, %s", request.getStreet(), ward.getName(), province.getName());
                address = Address.builder()
                        .provinceCode(request.getProvinceCode())
                        .wardCode(request.getWardCode())
                        .street(request.getStreet())
                        .fullAddress(fullAddress)
                        .user(currentUser)
                        .build();
                addressRepository.save(address);
                currentUser.addAddress(address);
                userRepository.save(currentUser);
                provinceCodeForShip = request.getProvinceCode();
            }
            String snapshotAddress = address.getFullAddress();

            // === 4. Tính phí ship ===
            BigDecimal shippingFee = calculateShippingFee(provinceCodeForShip);
            BigDecimal orderTotalBeforeDiscount = itemsTotal.add(shippingFee); // Tổng trước khi giảm

            // === 5. ÁP DỤNG COUPON – ĐÃ CÓ CAP, AN TOÀN 100% ===
            BigDecimal discountAmount = BigDecimal.ZERO;
            String appliedCouponCode = null;
            Coupon appliedCoupon = null;

            if (StringUtils.hasText(request.getCouponCode())) {
                String couponCode = request.getCouponCode().trim().toUpperCase();
                try {
                    ApplyCouponRequest applyReq = ApplyCouponRequest.builder()
                            .code(couponCode)
                            .orderTotal(orderTotalBeforeDiscount) // ← Dùng tổng trước giảm để tính % chính xác
                            .build();

                    // ← applyCoupon() đã có CAP + giới hạn đơn tối đa → an toàn tuyệt đối
                    discountAmount = couponService.applyCoupon(applyReq);

                    appliedCoupon = couponRepository.findByCode(couponCode)
                            .orElse(null);
                    appliedCouponCode = couponCode;

                    log.info("Áp dụng coupon thành công: {} → Giảm: {}đ", appliedCouponCode, discountAmount);

                } catch (NotFoundException | InvalidCouponException e) {
                    log.warn("Coupon không hợp lệ hoặc không áp dụng được: {} → Tiếp tục không giảm giá", e.getMessage());
                    // → Không throw, chỉ bỏ qua coupon → đơn vẫn tạo bình thường
                } catch (Exception e) {
                    log.error("Lỗi không mong muốn khi áp dụng coupon: {}", e.getMessage(), e);
                }
            }

            // === 6. Tính tổng cuối cùng ===
            BigDecimal totalAmount = orderTotalBeforeDiscount.subtract(discountAmount);

            // === 7. Tạo đơn hàng ===
            OrderStatus initialStatus = (request.getPaymentMethod() == PaymentMethod.SEPAY)
                    ? OrderStatus.WAITING_FOR_PAYMENT : OrderStatus.PENDING;

            Order order = Order.builder()
                    .user(currentUser)
                    .address(address)
                    .deliveryAddress(snapshotAddress)
                    .totalAmount(totalAmount)
                    .originalTotalAmount(orderTotalBeforeDiscount)
                    .discountAmount(discountAmount)
                    .shippingFee(shippingFee)
                    .status(initialStatus)
                    .orderStatus(initialStatus)
                    .paymentMethod(request.getPaymentMethod())
                    .paymentStatus(PaymentStatus.PENDING)
                    .shippingRecipientName(request.getRecipientName())
                    .shippingPhone(request.getPhone())
                    .notes(request.getNotes())
                    .orderItems(new ArrayList<>())
                    .build();

            if (appliedCoupon != null) {
                order.applyCoupon(appliedCoupon);
            }

            Order savedOrder = orderRepository.save(order);

            // === 8. Lưu chi tiết đơn + trừ kho + tăng usedCount ===
            try {
                List<OrderItem> orderItems = new ArrayList<>();
                for (Map.Entry<String, Integer> entry : productSizeQuantityMap.entrySet()) {
                    String[] parts = entry.getKey().split("-");
                    Long productId = Long.parseLong(parts[0]);
                    String size = parts[1];

                    Product product = productRepository.findById(productId).orElseThrow();

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

                // Trừ kho
                stockUpdates.forEach((key, qty) -> {
                    String[] parts = key.split("-");
                    ProductSize variant = productSizeRepository.findByProductIdAndSize(Long.parseLong(parts[0]), parts[1])
                            .orElseThrow();
                    variant.setStockQuantity(variant.getStockQuantity() - qty);
                    productSizeRepository.save(variant);
                });

                // Tăng lượt dùng coupon (chỉ khi có coupon hợp lệ)
                if (appliedCoupon != null) {
                    appliedCoupon.setUsedCount(appliedCoupon.getUsedCount() + 1);
                    couponRepository.save(appliedCoupon);
                    log.info("Đã tăng usedCount cho coupon {} → {}", appliedCoupon.getCode(), appliedCoupon.getUsedCount());
                }

                cartService.clearCart();

            } catch (Exception e) {
                log.error("Lỗi khi lưu chi tiết đơn hàng ID {}: {}", savedOrder.getId(), e.getMessage(), e);
                throw new RuntimeException("Lỗi hệ thống khi xử lý đơn hàng", e);
            }

            // === 9. Response ===
            Hibernate.initialize(savedOrder.getOrderItems());
            OrderResponse response = OrderResponse.fromEntity(savedOrder);
            response.setOriginalTotalAmount(orderTotalBeforeDiscount);
            response.setDiscountAmount(discountAmount);
            response.setCouponCode(appliedCouponCode);

            // SEPay QR
            if (request.getPaymentMethod() == PaymentMethod.SEPAY) {
                String qrUrl = String.format("https://qr.sepay.vn/img?bank=VietinBank&acc=109874753814&template=compact&amount=%d&des=SEVQR+TKPCCT+DH%d",
                        totalAmount.longValue(), savedOrder.getId());
                response.setQrCodeUrl(qrUrl);
                response.setBankInfo(Map.of(
                        "bankName", "VietinBank",
                        "accountNumber", "109874753814",
                        "accountHolder", "CAO CHIEN THANG",
                        "transferContent", "SEVQR TKPCCT DH" + savedOrder.getId()
                ));
            }

            sendOrderConfirmationEmailAsync(savedOrder);
            log.info("Tạo đơn hàng thành công: ID = {}", savedOrder.getId());

            return response;

        } catch (Exception e) {
            log.error("Tạo đơn hàng thất bại: {}", e.getMessage(), e);
            throw e;
        }
    }
    // 👈 THÊM MỚI: Helper method gửi email xác nhận đơn hàng (async)
    private void sendOrderConfirmationEmailAsync(Order order) {
        // Gọi async để không block transaction
        // Giả sử bạn có @Async method trong EmailService, hoặc gọi trực tiếp (nếu EmailService có @Async)
        emailService.sendOrderConfirmation(order.getUser().getEmail(), order);
        log.info("Order confirmation email queued for order: {}", order.getId());
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

            OrderStatus oldStatus = order.getStatus();  // 👈 THÊM: Lưu status cũ để check thay đổi

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

            Order savedOrder = orderRepository.save(order);

            // 👈 THÊM MỚI: Nếu status thay đổi, gửi email thông báo (tránh spam nếu gọi update nhiều lần)
            if (oldStatus != newStatus) {
                sendStatusUpdateEmailAsync(savedOrder, oldStatus, newStatus);
                log.info("Status update email queued for order: {} ({} -> {})", orderId, oldStatus, newStatus);
            }

            Hibernate.initialize(savedOrder.getOrderItems());
            return OrderResponse.fromEntity(savedOrder);
        } catch (InvalidOrderTransitionException e) {
            log.error("Transition error for order {}: {}", orderId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Update status failed for order {}: {}", orderId, e.getMessage());
            throw new RuntimeException("Lỗi cập nhật trạng thái: " + e.getMessage(), e);
        }
    }

    // 👈 THÊM MỚI: Helper method gửi email cập nhật trạng thái (async)
    private void sendStatusUpdateEmailAsync(Order order, OrderStatus oldStatus, OrderStatus newStatus) {
        // Gọi async
        emailService.sendStatusUpdate(order.getUser().getEmail(), order, oldStatus.name(), newStatus.name());
        log.info("Status update email queued for order: {}", order.getId());
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
            OrderStatus oldStatus = order.getStatus();  // 👈 THÊM: Lưu cũ
            validateTransition(order, OrderStatus.PROCESSING, null);
            order.setStatus(OrderStatus.PROCESSING);
            order.setOrderStatus(OrderStatus.PROCESSING);  // 👈 Sync
            Order savedOrder = orderRepository.save(order);

            // 👈 THÊM: Gửi email nếu thay đổi status
            if (oldStatus != OrderStatus.PROCESSING) {
                sendStatusUpdateEmailAsync(savedOrder, oldStatus, OrderStatus.PROCESSING);
            }

            return OrderResponse.fromEntity(savedOrder);
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

            OrderStatus oldStatus = order.getStatus();  // 👈 THÊM: Lưu cũ
            // Set PAID first
            order.setPaymentStatus(PaymentStatus.PAID);

            // Validate and set COMPLETED
            validateTransition(order, OrderStatus.COMPLETED, null);
            order.setStatus(OrderStatus.COMPLETED);
            order.setOrderStatus(OrderStatus.COMPLETED);  // 👈 Sync
            Order savedOrder = orderRepository.save(order);

            // 👈 THÊM: Gửi email
            if (oldStatus != OrderStatus.COMPLETED) {
                sendStatusUpdateEmailAsync(savedOrder, oldStatus, OrderStatus.COMPLETED);
            }

            Hibernate.initialize(savedOrder.getOrderItems());
            return OrderResponse.fromEntity(savedOrder);
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

            OrderStatus oldStatus = order.getStatus();  // 👈 THÊM: Lưu cũ

            // Allow cancel from PENDING/WAITING/PROCESSING/SHIPPED/DELIVERED (not COMPLETED)
            if (order.getStatus() == OrderStatus.COMPLETED) {
                throw new InvalidOrderTransitionException("Cannot cancel completed order");
            }
            validateTransition(order, OrderStatus.CANCELED, PaymentStatus.CANCELLED);

            restoreStock(order);
            order.setStatus(OrderStatus.CANCELED);
            order.setOrderStatus(OrderStatus.CANCELED);  // 👈 Sync
            order.setPaymentStatus(PaymentStatus.CANCELLED);
            Order savedOrder = orderRepository.save(order);

            // 👈 THÊM: Gửi email hủy đơn
            if (oldStatus != OrderStatus.CANCELED) {
                sendStatusUpdateEmailAsync(savedOrder, oldStatus, OrderStatus.CANCELED);
            }

            return OrderResponse.fromEntity(savedOrder);
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
            OrderStatus oldStatus = order.getStatus();  // 👈 THÊM: Lưu cũ
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
            Order savedOrder = orderRepository.save(order);

            // 👈 THÊM: Gửi email cập nhật từ webhook
            if (oldStatus != OrderStatus.PROCESSING) {
                sendStatusUpdateEmailAsync(savedOrder, oldStatus, OrderStatus.PROCESSING);
            }

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
            OrderStatus oldStatus = order.getStatus();  // 👈 THÊM: Lưu cũ
            if (!StringUtils.hasText(trackingNumber)) {
                throw new IllegalArgumentException("Tracking number required");
            }
            order.setTrackingNumber(trackingNumber);
            if (order.getStatus() == OrderStatus.PROCESSING) {
                validateTransition(order, OrderStatus.SHIPPED, null);
                order.setStatus(OrderStatus.SHIPPED);
                order.setOrderStatus(OrderStatus.SHIPPED);  // 👈 Sync
            }
            Order savedOrder = orderRepository.save(order);

            // 👈 THÊM: Gửi email nếu thay đổi status
            if (oldStatus != order.getStatus()) {
                sendStatusUpdateEmailAsync(savedOrder, oldStatus, order.getStatus());
            }

            Hibernate.initialize(savedOrder.getOrderItems());
            return OrderResponse.fromEntity(savedOrder);
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
                OrderStatus oldStatus = order.getStatus();  // 👈 THÊM: Lưu cũ
                try {
                    // Validate transition (should always pass for this case)
                    order.getStatus().canTransitionTo(OrderStatus.CANCELED, order.getPaymentMethod(), order.getPaymentStatus());

                    // Set statuses
                    order.setStatus(OrderStatus.CANCELED);
                    order.setOrderStatus(OrderStatus.CANCELED);  // 👈 Sync
                    order.setPaymentStatus(PaymentStatus.CANCELLED);

                    // Restore stock
                    restoreStock(order);

                    Order savedOrder = orderRepository.save(order);

                    // 👈 THÊM: Gửi email auto-cancel
                    sendStatusUpdateEmailAsync(savedOrder, oldStatus, OrderStatus.CANCELED);

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
                OrderStatus oldStatus = order.getStatus();  // 👈 THÊM: Lưu cũ
                try {
                    if (order.getStatus().canTransitionTo(OrderStatus.COMPLETED, order.getPaymentMethod(), order.getPaymentStatus())) {
                        order.setStatus(OrderStatus.COMPLETED);
                        order.setOrderStatus(OrderStatus.COMPLETED);  // 👈 Sync
                        Order savedOrder = orderRepository.save(order);

                        // 👈 THÊM: Gửi email auto-complete
                        sendStatusUpdateEmailAsync(savedOrder, oldStatus, OrderStatus.COMPLETED);

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

    @Override
    @Transactional(readOnly = true)
    public byte[] exportOrdersToExcel(OrderSearchRequest request) {
        log.info("Exporting orders with filters: {}", request);
        Specification<Order> spec = OrderSpecification.filterOrders(request);
        // Fetch ALL matching orders (no pageable for export)
        List<Order> orders = orderRepository.findAll(spec);
        log.info("Found {} orders for export", orders.size());

        try (Workbook workbook = new XSSFWorkbook()) {  // FIXED: Only Workbook in try-with-resources
            Sheet sheet = workbook.createSheet("Orders");  // Create Sheet inside try block

            // Create header row
            Row headerRow = sheet.createRow(0);
            String[] columns = {
                    "Order ID", "User Email", "Order Date", "Status", "Total Amount", "Original Total Amount",
                    "Discount Amount", "Coupon Code", "Payment Method", "Payment Status", "Province",
                    "Ward", "Recipient Name", "Phone", "Delivery Address", "Tracking Number"  // 👈 UPDATED: Removed District column, shifted rest
            };
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            int rowNum = 1;
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("dd/MM/yyyy HH:mm"));
            for (Order order : orders) {
                Hibernate.initialize(order.getOrderItems()); // Ensure lazy load
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(order.getId());
                row.createCell(1).setCellValue(order.getUser().getEmail());
                Cell dateCell = row.createCell(2);
                dateCell.setCellValue(order.getCreatedAt());
                dateCell.setCellStyle(dateStyle);
                row.createCell(3).setCellValue(order.getStatus().name());
                row.createCell(4).setCellValue(order.getTotalAmount().doubleValue());
                row.createCell(5).setCellValue(order.getOriginalTotalAmount() != null ? order.getOriginalTotalAmount().doubleValue() : 0.0);
                row.createCell(6).setCellValue(order.getDiscountAmount() != null ? order.getDiscountAmount().doubleValue() : 0.0);

                // 👈 FIXED: Null check for coupon
                row.createCell(7).setCellValue(order.getCoupon() != null ? order.getCoupon().getCode() : "");
                row.createCell(8).setCellValue(order.getPaymentMethod().name());
                row.createCell(9).setCellValue(order.getPaymentStatus().name());
                // Province: Fetch from address
                String provinceName = order.getAddress() != null && order.getAddress().getProvinceCode() != null
                        ? provinceRepository.findById(order.getAddress().getProvinceCode()).map(Province::getName).orElse("Unknown")
                        : "Unknown";
                row.createCell(10).setCellValue(provinceName);
                // 👈 NEW: Ward (no District)
                String wardName = order.getAddress() != null && order.getAddress().getWardCode() != null
                        ? wardRepository.findById(order.getAddress().getWardCode()).map(Ward::getName).orElse("Unknown")
                        : "Unknown";
                row.createCell(11).setCellValue(wardName);
                // 👈 SHIFTED: Recipient Name (was 13 → 12, adjusted for no District)
                row.createCell(12).setCellValue(order.getShippingRecipientName());
                // 👈 SHIFTED: Phone (was 14 → 13)
                row.createCell(13).setCellValue(order.getShippingPhone());
                // 👈 SHIFTED: Delivery Address (was 15 → 14)
                row.createCell(14).setCellValue(order.getDeliveryAddress());
                // 👈 SHIFTED: Tracking Number (was 16 → 15)
                row.createCell(15).setCellValue(order.getTrackingNumber() != null ? order.getTrackingNumber() : "");

                // Auto-size columns (move outside loop for efficiency)
            }
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Write to ByteArrayOutputStream
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Error generating Excel for export: {}", e.getMessage(), e);
            throw new RuntimeException("Lỗi tạo file Excel: " + e.getMessage(), e);
        }
    }

    // Trong OrderServiceImpl
    // OrderServiceImpl.java
    @Override
    public List<ProductCardDto> getBestSellingProductsLast30Days(Integer limit) {
        int safeLimit = (limit == null || limit <= 0) ? 12 : Math.min(limit, 50);

        return orderRepository.findBestSellingProductsRaw(safeLimit)
                .stream()
                .map(row -> {
                    String imageUrl = (String) row[2];
                    if (imageUrl == null || imageUrl.isBlank()) {
                        imageUrl = "/images/default-product.jpg";
                    }

                    BigDecimal price = row[3] instanceof BigDecimal bd ? bd : BigDecimal.valueOf(((Number) row[3]).doubleValue());
                    Double avgRating = row[5] instanceof Number n ? n.doubleValue() : 0.0;
                    Integer reviewCount = row[6] instanceof Number n ? n.intValue() : 0;
                    String brandName = (String) row[7]; // ← Đây chính là cái mới!

                    return new ProductCardDto(
                            ((Number) row[0]).longValue(),
                            (String) row[1],
                            imageUrl,
                            price,
                            Math.round(avgRating * 10.0) / 10.0,
                            reviewCount,
                            brandName,   // ← Giờ có tên thương hiệu rồi!
                            null
                    );
                })
                .toList();
    }
}