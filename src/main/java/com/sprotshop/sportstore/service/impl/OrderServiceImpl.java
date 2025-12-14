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
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hibernate.Hibernate;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

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
    private final CouponService couponService;
    private final CouponRepository couponRepository;
    private final EmailService emailService;

    private static final long WAITING_TIMEOUT_MINUTES = 5;
    private static final long COMPLETE_AFTER_DAYS = 7;

    @Override
    @Transactional
    @CacheEvict(value = {"userOrders", "allOrders"}, allEntries = true)
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

            // === 2. Tính tổng tiền sản phẩm + kiểm tra tồn kho (CHỈ KIỂM TRA, CHƯA TRỪ) ===
            Map<String, Integer> productSizeQuantityMap = new HashMap<>();
            Map<String, BigDecimal> priceAtOrderMap = new HashMap<>();
            BigDecimal itemsTotal = BigDecimal.ZERO;

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
            BigDecimal orderTotalBeforeDiscount = itemsTotal.add(shippingFee);

            // === 5. ÁP DỤNG COUPON ===
            // === 5. ÁP DỤNG COUPON ===
            BigDecimal discountAmount = BigDecimal.ZERO;
            String appliedCouponCode = null;
            Coupon appliedCoupon = null;

            if (StringUtils.hasText(request.getCouponCode())) {
                String couponCode = request.getCouponCode().trim().toUpperCase();
                log.info("Áp dụng coupon: {} cho đơn hàng", couponCode);

                try {
                    appliedCoupon = couponRepository.findByCode(couponCode)
                            .orElseThrow(() -> new NotFoundException("Không tìm thấy coupon: " + couponCode));

                    LocalDateTime now = LocalDateTime.now();

                    if (now.isBefore(appliedCoupon.getStartDate()) || now.isAfter(appliedCoupon.getEndDate())) {
                        throw new InvalidCouponException("Coupon đã hết hạn hoặc chưa có hiệu lực");
                    }

                    if (orderTotalBeforeDiscount.compareTo(appliedCoupon.getMinOrderValue()) < 0) {
                        throw new InvalidCouponException(
                                String.format("Đơn hàng phải từ %,dđ để sử dụng coupon này",
                                        appliedCoupon.getMinOrderValue().longValue()));
                    }

                    if (appliedCoupon.getTotalUsageLimit() != null &&
                            appliedCoupon.getUsedCount() >= appliedCoupon.getTotalUsageLimit()) {
                        throw new InvalidCouponException("Coupon đã hết lượt sử dụng toàn hệ thống");
                    }

                    long userUsedCount = orderRepository.countByUserIdAndCouponIdAndStatus(
                            userId, appliedCoupon.getId(), OrderStatus.COMPLETED);

                    if (appliedCoupon.getMaxUsagePerUser() != null &&
                            userUsedCount >= appliedCoupon.getMaxUsagePerUser()) {
                        throw new InvalidCouponException("Bạn đã sử dụng hết lượt coupon này");
                    }

                    boolean isCouponApplicable = false;
                    if (appliedCoupon.getScope() == null ||
                            appliedCoupon.getScope() == com.sprotshop.sportstore.Enum.CouponScope.ALL_PRODUCTS) {
                        isCouponApplicable = true;
                    } else {
                        for (CartItem cartItem : cart.getCartItems()) {
                            Product product = cartItem.getProduct();
                            switch (appliedCoupon.getScope()) {
                                case SPECIFIC_PRODUCTS:
                                    if (appliedCoupon.getApplicableProducts() != null &&
                                            appliedCoupon.getApplicableProducts().stream()
                                                    .anyMatch(p -> p.getId().equals(product.getId()))) {
                                        isCouponApplicable = true;
                                    }
                                    break;
                                case CATEGORY:
                                    if (appliedCoupon.getApplicableCategory() != null &&
                                            product.getProductCategory() != null &&
                                            product.getProductCategory().getId().equals(appliedCoupon.getApplicableCategory().getId())) {
                                        isCouponApplicable = true;
                                    }
                                    break;
                                case BRAND:
                                    if (appliedCoupon.getApplicableBrand() != null &&
                                            product.getBrand() != null &&
                                            product.getBrand().getId().equals(appliedCoupon.getApplicableBrand().getId())) {
                                        isCouponApplicable = true;
                                    }
                                    break;
                            }
                            if (isCouponApplicable) break;
                        }
                    }

                    if (!isCouponApplicable) {
                        throw new InvalidCouponException("Coupon không áp dụng cho sản phẩm trong giỏ hàng");
                    }

                    discountAmount = calculateCouponDiscount(appliedCoupon, itemsTotal);

                    if (discountAmount.compareTo(BigDecimal.ZERO) < 0) discountAmount = BigDecimal.ZERO;
                    if (discountAmount.compareTo(itemsTotal) > 0) discountAmount = itemsTotal;

                    appliedCouponCode = couponCode;
                    log.info("Áp dụng coupon thành công: {} → Giảm: {}đ", couponCode, discountAmount);

                } catch (NotFoundException | InvalidCouponException e) {
                    log.warn("Coupon không hợp lệ: {} → {}", couponCode, e.getMessage());
                    // THÊM 2 DÒNG QUAN TRỌNG NÀY:
                    throw new InvalidCouponException("Coupon không hợp lệ: " + e.getMessage());
                } catch (Exception e) {
                    log.error("Lỗi áp dụng coupon: {}", e.getMessage(), e);
                    throw new InvalidCouponException("Lỗi xử lý coupon: " + e.getMessage());
                }
            }
            // === 6. Tính tổng cuối cùng ===
            BigDecimal totalAmount = orderTotalBeforeDiscount.subtract(discountAmount);
            if (totalAmount.compareTo(BigDecimal.ZERO) < 0) totalAmount = BigDecimal.ZERO;

            // === 7. Tạo đơn hàng - trạng thái ban đầu luôn là PENDING ===
            Order order = Order.builder()
                    .user(currentUser)
                    .address(address)
                    .deliveryAddress(snapshotAddress)
                    .totalAmount(totalAmount)
                    .originalTotalAmount(orderTotalBeforeDiscount)
                    .discountAmount(discountAmount)
                    .shippingFee(shippingFee)
                    .status(OrderStatus.PENDING)
                    .orderStatus(OrderStatus.PENDING)
                    .paymentMethod(request.getPaymentMethod())
                    .paymentStatus(request.getPaymentMethod() == PaymentMethod.SEPAY ?
                            PaymentStatus.PENDING : PaymentStatus.PENDING) // <-- FIX: COD cũng là PENDING
                    .shippingRecipientName(request.getRecipientName())
                    .shippingPhone(request.getPhone())
                    .notes(request.getNotes())
                    .orderItems(new ArrayList<>())
                    .build();
            if (appliedCoupon != null && appliedCouponCode != null) {
                order.applyCoupon(appliedCoupon);
            }

            Order savedOrder = orderRepository.save(order);

            // === 8. Lưu chi tiết đơn hàng (CHƯA TRỪ KHO) ===
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

                // Tăng usedCount coupon ngay khi tạo đơn (vì đã áp dụng)
                if (appliedCoupon != null && appliedCouponCode != null) {
                    appliedCoupon.setUsedCount(appliedCoupon.getUsedCount() + 1);
                    couponRepository.save(appliedCoupon);
                }

                cartService.clearCart();

            } catch (Exception e) {
                log.error("Lỗi lưu chi tiết đơn hàng ID {}: {}", savedOrder.getId(), e.getMessage(), e);
                throw new RuntimeException("Lỗi hệ thống khi xử lý đơn hàng", e);
            }

            // === 9. Response ===
            Hibernate.initialize(savedOrder.getOrderItems());
            OrderResponse response = OrderResponse.fromEntity(savedOrder);
            response.setOriginalTotalAmount(orderTotalBeforeDiscount);
            response.setDiscountAmount(discountAmount);
            response.setCouponCode(appliedCouponCode);

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
    private BigDecimal calculateShippingFee(Integer provinceCode) {
        if (provinceCode != null && provinceCode >= 1 && provinceCode <= 10) {
            return new BigDecimal("20000");
        } else {
            return new BigDecimal("30000");
        }
    }
    private void deductStock(Order order) {
        if (CollectionUtils.isEmpty(order.getOrderItems())) return;

        List<ProductSize> variantsToUpdate = new ArrayList<>();
        for (OrderItem item : order.getOrderItems()) {
            ProductSize variant = productSizeRepository
                    .findByProductIdAndSize(item.getProduct().getId(), item.getSize())
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy variant"));

            if (variant.getStockQuantity() < item.getQuantity()) {
                throw new IllegalStateException("Không đủ tồn kho thực tế để xuất hàng cho đơn " + order.getId());
            }

            variant.setStockQuantity(variant.getStockQuantity() - item.getQuantity());
            variantsToUpdate.add(variant);
        }
        productSizeRepository.saveAll(variantsToUpdate);
        log.info("Đã trừ tồn kho cho đơn hàng COMPLETED: {}", order.getId());
    }

    /**
     * Tính toán số tiền giảm giá từ coupon (CHỈ TÍNH TRÊN TỔNG GIÁ TRỊ SẢN PHẨM)
     */
    private BigDecimal calculateCouponDiscount(Coupon coupon, BigDecimal itemsTotal) {
        // Giữ nguyên logic cũ
        BigDecimal discount = BigDecimal.ZERO;
        if (coupon.getType() == com.sprotshop.sportstore.Enum.CouponType.FIXED) {
            discount = coupon.getDiscountAmount();
        } else if (coupon.getType() == com.sprotshop.sportstore.Enum.CouponType.PERCENTAGE) {
            discount = itemsTotal.multiply(coupon.getDiscountPercentage())
                    .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
            if (coupon.getMaxDiscountAmount() != null && discount.compareTo(coupon.getMaxDiscountAmount()) > 0) {
                discount = coupon.getMaxDiscountAmount();
            }
            if (coupon.getMaxApplicableOrderValue() != null && itemsTotal.compareTo(coupon.getMaxApplicableOrderValue()) > 0) {
                BigDecimal capped = coupon.getMaxApplicableOrderValue();
                discount = capped.multiply(coupon.getDiscountPercentage())
                        .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
                if (coupon.getMaxDiscountAmount() != null && discount.compareTo(coupon.getMaxDiscountAmount()) > 0) {
                    discount = coupon.getMaxDiscountAmount();
                }
            }
        }
        return discount.setScale(2, RoundingMode.HALF_UP);
    }
    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderDetails(Long orderId) {
        User currentUser = userService.getCurrentLoggedInUser();
        Order order = orderRepository.findByIdAndUserId(orderId, currentUser.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng: " + orderId));
        Hibernate.initialize(order.getOrderItems());
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

    @Override
    @Transactional
    @CacheEvict(value = {"userOrders", "allOrders"}, allEntries = true)
    public OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng: " + orderId));

        OrderStatus oldStatus = order.getStatus();

        // Không cho thay đổi nếu đã COMPLETED hoặc CANCELED
        if (oldStatus == OrderStatus.COMPLETED || oldStatus == OrderStatus.CANCELED) {
            throw new InvalidOrderTransitionException("Không thể thay đổi trạng thái đơn hàng đã hoàn thành hoặc đã hủy");
        }

        // Chỉ cho phép chuyển từ PENDING sang COMPLETED hoặc CANCELED
        if (oldStatus != OrderStatus.PENDING) {
            throw new InvalidOrderTransitionException("Chỉ có thể thay đổi trạng thái từ đơn mới (PENDING)");
        }

        // Khi chuyển sang COMPLETED → TRỪ KHO + xác nhận thanh toán nếu cần
        if (newStatus == OrderStatus.COMPLETED) {
            deductStock(order);

            // Auto xác nhận thanh toán
            if (order.getPaymentMethod() == PaymentMethod.SEPAY && order.getPaymentStatus() == PaymentStatus.PENDING) {
                order.setPaymentStatus(PaymentStatus.PAID);
            }
            if (order.getPaymentMethod() == PaymentMethod.COD) {
                order.setPaymentStatus(PaymentStatus.PAID);
            }
        }

        // Khi chuyển sang CANCELED → chỉ từ PENDING → không cần hoàn kho
        if (newStatus == OrderStatus.CANCELED) {
            order.setPaymentStatus(PaymentStatus.CANCELLED);
        }

        order.setStatus(newStatus);
        order.setOrderStatus(newStatus);
        Order savedOrder = orderRepository.save(order);

        sendStatusUpdateEmailAsync(savedOrder, oldStatus, newStatus);

        Hibernate.initialize(savedOrder.getOrderItems());
        return OrderResponse.fromEntity(savedOrder);
    }
    @Override
    @Transactional
    @CacheEvict(value = {"userOrders", "allOrders"}, allEntries = true)
    public OrderResponse cancelOrder(Long orderId) {
        User currentUser = userService.getCurrentLoggedInUser();
        Order order = orderRepository.findByIdAndUserId(orderId, currentUser.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng: " + orderId));

        OrderStatus oldStatus = order.getStatus();

        if (oldStatus == OrderStatus.COMPLETED) {
            throw new InvalidOrderTransitionException("Không thể hủy đơn hàng đã hoàn thành");
        }
        if (oldStatus == OrderStatus.CANCELED) {
            throw new InvalidOrderTransitionException("Đơn hàng đã bị hủy trước đó");
        }

        // Chỉ hủy được khi đang PENDING → chưa trừ kho → không cần hoàn
        order.setStatus(OrderStatus.CANCELED);
        order.setOrderStatus(OrderStatus.CANCELED);
        order.setPaymentStatus(PaymentStatus.CANCELLED);

        Order savedOrder = orderRepository.save(order);
        sendStatusUpdateEmailAsync(savedOrder, oldStatus, OrderStatus.CANCELED);

        return OrderResponse.fromEntity(savedOrder);
    }
    @Override
    @Transactional
    public void handleSepayWebhook(SepayWebhookRequest webhook) {
        try {
            if (!"in".equalsIgnoreCase(webhook.getTransferType())) return;

            Transaction tx = transactionService.saveFromWebhook(webhook);

            String content = webhook.getContent() != null ? webhook.getContent().trim() : "";
            if (content.isBlank()) return;

            Pattern pattern = Pattern.compile("\\bDH\\s*(\\d+)\\b", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(content);
            if (!matcher.find()) return;

            Long orderId = Long.valueOf(matcher.group(1));

            Optional<Order> optionalOrder = orderRepository.findByIdAndTotalAmountAndPaymentStatus(
                    orderId, webhook.getTransferAmount(), PaymentStatus.PENDING);
            if (optionalOrder.isEmpty()) return;

            Order order = optionalOrder.get();
            if (order.getPaymentStatus() == PaymentStatus.PAID) return;

            order.setPaymentStatus(PaymentStatus.PAID);
            Order savedOrder = orderRepository.save(order);

            sendStatusUpdateEmailAsync(savedOrder, order.getStatus(), order.getStatus());
            log.info("SUCCESS: Đã xác nhận thanh toán SEPAY cho đơn {}", orderId);
        } catch (Exception e) {
            log.error("Webhook error: {}", e.getMessage(), e);
            throw e;
        }
    }
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
            List<String> emails = orderRepository.findUniqueUserEmails();
            return emails.stream()
                    .map(email -> Map.of("value", email, "label", email))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch unique emails: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderStatus> getPossibleNextStatuses(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng: " + orderId));

        // Không thể chuyển nếu đã hoàn thành hoặc hủy
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELED) {
            return List.of();
        }

        // Trả về tất cả trạng thái có thể (trừ COMPLETED và CANCELED)
        List<OrderStatus> allStatuses = Arrays.asList(OrderStatus.values());
        return allStatuses.stream()
                .filter(status -> status != order.getStatus() &&
                        status != OrderStatus.COMPLETED &&
                        status != OrderStatus.CANCELED)
                .collect(Collectors.toList());
    }

    // Scheduler: Auto-cancel unpaid SEPAY orders

    @Override
    @Transactional(readOnly = true)
    public byte[] exportOrdersToExcel(OrderSearchRequest request) {
        log.info("Exporting orders with filters: {}", request);
        Specification<Order> spec = OrderSpecification.filterOrders(request);
        List<Order> orders = orderRepository.findAll(spec);
        log.info("Found {} orders for export", orders.size());

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Đơn hàng");

            // === CÁC CỘT TRONG FILE EXCEL (theo logic mới) ===
            String[] columns = {
                    "Mã đơn hàng",
                    "Email khách hàng",
                    "Ngày đặt hàng",
                    "Trạng thái đơn",
                    "Thành tiền",
                    "Tổng gốc",
                    "Giảm giá",
                    "Mã giảm giá",
                    "Hình thức thanh toán",
                    "Tình trạng thanh toán",  // Giữ lại vì vẫn cần biết đã paid hay chưa
                    "Tỉnh/Thành phố",
                    "Phường/Xã",
                    "Người nhận",
                    "Số điện thoại",
                    "Địa chỉ giao hàng",
                    "Ghi chú"
            };

            // === Header style ===
            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 13);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            // === Style cho dữ liệu ===
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("dd/MM/yyyy HH:mm"));

            CellStyle moneyStyle = workbook.createCellStyle();
            moneyStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));

            int rowNum = 1;
            for (Order order : orders) {
                Hibernate.initialize(order.getOrderItems());
                Row row = sheet.createRow(rowNum++);
                row.setHeightInPoints(25);

                // 0. Mã đơn
                row.createCell(0).setCellValue(order.getId());

                // 1. Email khách
                row.createCell(1).setCellValue(order.getUser() != null ? order.getUser().getEmail() : "");

                // 2. Ngày đặt hàng
                Cell dateCell = row.createCell(2);
                dateCell.setCellValue(order.getCreatedAt());
                dateCell.setCellStyle(dateStyle);

                // 3. Trạng thái đơn - chỉ 3 trạng thái mới
                row.createCell(3).setCellValue(statusTiengViet(order.getStatus()));

                // 4. Thành tiền (sau giảm giá)
                Cell totalCell = row.createCell(4);
                totalCell.setCellValue(order.getTotalAmount() != null ? order.getTotalAmount().doubleValue() : 0);
                totalCell.setCellStyle(moneyStyle);

                // 5. Tổng gốc (trước giảm giá)
                Cell originalCell = row.createCell(5);
                originalCell.setCellValue(order.getOriginalTotalAmount() != null ? order.getOriginalTotalAmount().doubleValue() : 0);
                originalCell.setCellStyle(moneyStyle);

                // 6. Giảm giá
                Cell discountCell = row.createCell(6);
                discountCell.setCellValue(order.getDiscountAmount() != null ? order.getDiscountAmount().doubleValue() : 0);
                discountCell.setCellStyle(moneyStyle);

                // 7. Mã giảm giá
                row.createCell(7).setCellValue(order.getCoupon() != null ? order.getCoupon().getCode() : "");

                // 8. Hình thức thanh toán
                row.createCell(8).setCellValue(paymentMethodTiengViet(order.getPaymentMethod()));

                // 9. Tình trạng thanh toán (giữ lại vì vẫn cần cho SEPAY/COD)
                row.createCell(9).setCellValue(paymentStatusTiengViet(order.getPaymentStatus()));

                // 10. Tỉnh/Thành phố
                String provinceName = "Không xác định";
                if (order.getAddress() != null && order.getAddress().getProvinceCode() != null) {
                    provinceName = provinceRepository.findById(order.getAddress().getProvinceCode())
                            .map(Province::getName)
                            .orElse("Không xác định");
                }
                row.createCell(10).setCellValue(provinceName);

                // 11. Phường/Xã
                String wardName = "Không xác định";
                if (order.getAddress() != null && order.getAddress().getWardCode() != null) {
                    wardName = wardRepository.findById(order.getAddress().getWardCode())
                            .map(Ward::getName)
                            .orElse("Không xác định");
                }
                row.createCell(11).setCellValue(wardName);

                // 12. Người nhận
                row.createCell(12).setCellValue(order.getShippingRecipientName() != null ? order.getShippingRecipientName() : "");

                // 13. Số điện thoại
                row.createCell(13).setCellValue(order.getShippingPhone() != null ? order.getShippingPhone() : "");

                // 14. Địa chỉ giao hàng
                row.createCell(14).setCellValue(order.getDeliveryAddress() != null ? order.getDeliveryAddress() : "");

                // 15. Ghi chú
                row.createCell(15).setCellValue(order.getNotes() != null ? order.getNotes() : "");
            }

            // Tự động điều chỉnh độ rộng cột
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
                if (sheet.getColumnWidth(i) < 4000) {
                    sheet.setColumnWidth(i, 5000);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            log.error("Error generating Excel for export: {}", e.getMessage(), e);
            throw new RuntimeException("Lỗi tạo file Excel: " + e.getMessage(), e);
        }
    }
    private String statusTiengViet(OrderStatus status) {
        return switch (status) {
            case PENDING    -> "Chờ xử lý";
            case COMPLETED  -> "Hoàn thành";
            case CANCELED   -> "Đã hủy";
            default         -> status.name();
        };
    }

    private String paymentMethodTiengViet(PaymentMethod method) {
        return switch (method) {
            case COD   -> "Thanh toán khi nhận hàng (COD)";
            case SEPAY -> "Chuyển khoản ngân hàng";
            default    -> method.name();
        };
    }

    private String paymentStatusTiengViet(PaymentStatus status) {
        return switch (status) {
            case PENDING   -> "Chưa thanh toán";
            case PAID      -> "Đã thanh toán";
            case CANCELLED -> "Đã hủy";
            case REFUNDED  -> "Đã hoàn tiền";
            default        -> status.name();
        };
    }

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
                    String brandName = (String) row[7];

                    return new ProductCardDto(
                            ((Number) row[0]).longValue(),
                            (String) row[1],
                            imageUrl,
                            price,
                            Math.round(avgRating * 10.0) / 10.0,
                            reviewCount,
                            brandName,
                            null
                    );
                })
                .toList();
    }

    // Helper method gửi email xác nhận đơn hàng
    private void sendOrderConfirmationEmailAsync(Order order) {
        try {
            emailService.sendOrderConfirmation(order.getUser().getEmail(), order);
            log.info("Order confirmation email queued for order: {}", order.getId());
        } catch (Exception e) {
            log.error("Failed to send order confirmation email: {}", e.getMessage());
        }
    }

    // Helper method gửi email cập nhật trạng thái
    private void sendStatusUpdateEmailAsync(Order order, OrderStatus oldStatus, OrderStatus newStatus) {
        try {
            emailService.sendStatusUpdate(order.getUser().getEmail(), order, oldStatus.name(), newStatus.name());
            log.info("Status update email queued for order: {}", order.getId());
        } catch (Exception e) {
            log.error("Failed to send status update email: {}", e.getMessage());
        }
    }

    @Scheduled(fixedRate = 60000) // Chạy mỗi 1 phút để kiểm tra nhanh hơn (tùy chọn)
    @Transactional
    public void autoCancelUnpaidSepayOrders() {
        try {
            LocalDateTime threshold = LocalDateTime.now().minusMinutes(WAITING_TIMEOUT_MINUTES); // 5 phút

            List<Order> unpaidOrders = orderRepository.findByStatusAndPaymentMethodAndPaymentStatusAndCreatedAtBefore(
                    OrderStatus.PENDING, PaymentMethod.SEPAY, PaymentStatus.PENDING, threshold);

            if (unpaidOrders.isEmpty()) {
                return;
            }

            log.info("Tự động hủy {} đơn SEPAY chưa thanh toán quá {} phút", unpaidOrders.size(), WAITING_TIMEOUT_MINUTES);

            for (Order order : unpaidOrders) {
                try {
                    OrderStatus oldStatus = order.getStatus();

                    order.setStatus(OrderStatus.CANCELED);
                    order.setOrderStatus(OrderStatus.CANCELED);
                    order.setPaymentStatus(PaymentStatus.CANCELLED);

                    Order savedOrder = orderRepository.save(order);

                    sendStatusUpdateEmailAsync(savedOrder, oldStatus, OrderStatus.CANCELED);

                    log.info("Đã tự động hủy đơn SEPAY ID {} do không thanh toán sau {} phút", order.getId(), WAITING_TIMEOUT_MINUTES);
                } catch (Exception e) {
                    log.error("Lỗi tự động hủy đơn {}: {}", order.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Lỗi scheduler autoCancelUnpaidSepayOrders: {}", e.getMessage());
        }
    }
}