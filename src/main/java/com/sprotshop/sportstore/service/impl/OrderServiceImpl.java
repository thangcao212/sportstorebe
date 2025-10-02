// Updated OrderServiceImpl.java - Fixed logic in handleSepayWebhook to only process "in" transfers, compare with amountIn logic, and ensure status checks align with original demo
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    @Override
    @Transactional
    @CacheEvict(value = {"userOrders", "allOrders"}, key = "#userService.getCurrentLoggedInUser().id + '-*'", allEntries = true)
    public OrderResponse createOrderFromCart(CreateOrderRequest request) {
        User currentUser = userService.getCurrentLoggedInUser();
        Long userId = currentUser.getId();
        log.info("Creating order for userId: {}", userId);

        Province province = provinceRepository.findById(request.getProvinceCode())
                .orElseThrow(() -> new NotFoundException("Mã tỉnh không hợp lệ: " + request.getProvinceCode()));
        District district = districtRepository.findById(request.getDistrictCode())
                .orElseThrow(() -> new NotFoundException("Mã huyện không hợp lệ: " + request.getDistrictCode()));
        Ward ward = wardRepository.findById(request.getWardCode())
                .orElseThrow(() -> new NotFoundException("Mã xã không hợp lệ: " + request.getWardCode()));

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
            Product product = cartItem.getProduct();
            String size = cartItem.getSize();
            int quantity = cartItem.getQuantity();
            String productSizeKey = product.getId() + "-" + size;

            ProductSize variant = productSizeRepository.findByProductIdAndSize(product.getId(), size)
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy kích thước sản phẩm: " + product.getId() + ", size: " + size));
            if (variant.getStockQuantity() < quantity) {
                throw new IllegalStateException("Sản phẩm hết hàng cho kích thước " + size + ": " + product.getName());
            }

            BigDecimal price = BigDecimal.valueOf(product.getPrice());
            productSizeQuantityMap.put(productSizeKey, quantity);
            priceAtOrderMap.put(productSizeKey, price);
            totalAmount = totalAmount.add(price.multiply(BigDecimal.valueOf(quantity)));
            stockUpdates.put(productSizeKey, quantity);
        }

        String fullAddress = String.format("%s, %s, %s, %s", request.getStreet(), ward.getName(), district.getName(), province.getName());
        Address address = Address.builder()
                .provinceCode(request.getProvinceCode())
                .districtCode(request.getDistrictCode())
                .wardCode(request.getWardCode())
                .street(request.getStreet())
                .fullAddress(fullAddress)
                .build();
        addressRepository.save(address);

        // Gán trạng thái dựa trên paymentMethod
        OrderStatus orderStatus = request.getPaymentMethod() == PaymentMethod.SEPAY ? OrderStatus.WAITING_FOR_PAYMENT : OrderStatus.PENDING;
        PaymentStatus paymentStatus = PaymentStatus.PENDING;

        Order order = Order.builder()
                .user(currentUser)
                .address(address)
                .totalAmount(totalAmount)
                .status(orderStatus)
                .paymentMethod(request.getPaymentMethod())
                .paymentStatus(paymentStatus)
                .shippingRecipientName(request.getRecipientName())
                .shippingPhone(request.getPhone())
                .notes(request.getNotes())
                .orderItems(new ArrayList<>())
                .build();
        Order savedOrder = orderRepository.save(order);

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
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderDetails(Long orderId) {
        User currentUser = userService.getCurrentLoggedInUser();
        Order order = orderRepository.findByIdAndUserId(orderId, currentUser.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng: " + orderId));
        Hibernate.initialize(order.getOrderItems());
        return OrderResponse.fromEntity(order);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"userOrders", "allOrders"}, key = "#userService.getCurrentLoggedInUser().id + '-*'", allEntries = true)
    public OrderResponse cancelOrder(Long orderId) {
        User currentUser = userService.getCurrentLoggedInUser();
        Order order = orderRepository.findByIdAndUserId(orderId, currentUser.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng: " + orderId));

        if (!List.of(OrderStatus.PENDING, OrderStatus.WAITING_FOR_PAYMENT, OrderStatus.PROCESSING)
                .contains(order.getStatus())) {
            throw new IllegalStateException("Không thể hủy đơn hàng ở trạng thái: " + order.getStatus());
        }

        Hibernate.initialize(order.getOrderItems());

        if (!order.getOrderItems().isEmpty()) {
            List<ProductSize> variantsToUpdate = new ArrayList<>();

            order.getOrderItems().forEach(item -> {
                ProductSize variant = productSizeRepository.findByProductIdAndSize(
                        item.getProduct().getId(),
                        item.getSize()
                ).orElseThrow(() -> new NotFoundException(
                        "Không tìm thấy kích thước sản phẩm: " + item.getProduct().getId() + ", size: " + item.getSize()
                ));
                variant.setStockQuantity(variant.getStockQuantity() + item.getQuantity());
                variantsToUpdate.add(variant);
            });

            productSizeRepository.saveAll(variantsToUpdate);
        }

        order.setStatus(OrderStatus.CANCELED);
        return OrderResponse.fromEntity(orderRepository.save(order));
    }

    @Override
    @Transactional
    @CacheEvict(value = {"userOrders", "allOrders"}, key = "#userService.getCurrentLoggedInUser().id + '-*'", allEntries = true)
    public OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng: " + orderId));
        order.setStatus(newStatus);
        Hibernate.initialize(order.getOrderItems());
        return OrderResponse.fromEntity(orderRepository.save(order));
    }

    @Override
    @Transactional
    @CacheEvict(value = {"userOrders", "allOrders"}, key = "#userService.getCurrentLoggedInUser().id + '-*'", allEntries = true)
    public OrderResponse addTrackingNumber(Long orderId, String trackingNumber) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng: " + orderId));
        order.setTrackingNumber(trackingNumber);
        if (order.getStatus() == OrderStatus.PROCESSING) {
            order.setStatus(OrderStatus.SHIPPED);
        }
        Hibernate.initialize(order.getOrderItems());
        return OrderResponse.fromEntity(orderRepository.save(order));
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
    public void handleSepayWebhook(SepayWebhookRequest webhook) {
        log.info("Processing webhook: sepayTransactionId={}, referenceCode={}, content='{}', amount={}, type={}",
                webhook.getId(), webhook.getReferenceCode(), webhook.getContent(), webhook.getTransferAmount(), webhook.getTransferType());

        // Only process incoming transfers
        if (!"in".equalsIgnoreCase(webhook.getTransferType())) {
            log.info("Ignoring non-incoming transfer: type={}, SepayTransactionId={}", webhook.getTransferType(), webhook.getId());
            return;
        }

        // Lưu transaction (sẽ throw nếu duplicate)
        Transaction tx;
        try {
            tx = transactionService.saveFromWebhook(webhook);
            log.info("Saved transaction: id={}, amountIn={}", tx.getId(), tx.getAmountIn());
        } catch (IllegalStateException e) {
            log.warn("Duplicate transaction ignored: {}", e.getMessage());
            return;  // Không throw, chỉ log để SePay không retry vô tận
        }

        // Regex tìm mã đơn hàng DH123 (flexible: trim space, case-insensitive)
        String content = webhook.getContent() != null ? webhook.getContent().trim() : "";
        if (content.isBlank()) {
            log.warn("Webhook content empty, skipping. SepayTransactionId={}", webhook.getId());
            return;
        }

        Pattern pattern = Pattern.compile("\\bDH\\s*(\\d+)\\b", Pattern.CASE_INSENSITIVE);  // Fix: \b word boundary, \s* cho space
        Matcher matcher = pattern.matcher(content);

        if (!matcher.find()) {
            log.warn("No order code found in content: '{}'. SepayTransactionId={}", content, webhook.getId());
            return;
        }

        Long orderId;
        try {
            orderId = Long.valueOf(matcher.group(1));
            log.info("Extracted orderId: {} from content", orderId);
        } catch (NumberFormatException e) {
            log.error("Failed to parse orderId from '{}': {}", content, e.getMessage());
            return;
        }

        // Tìm order: ID + total exact match + status phù hợp (PENDING hoặc WAITING_FOR_PAYMENT)
        Optional<Order> optionalOrder = orderRepository.findByIdAndTotalAmountAndPaymentStatus(
                orderId, webhook.getTransferAmount(), PaymentStatus.PENDING);  // Hoặc add overload cho WAITING_FOR_PAYMENT nếu cần
        if (optionalOrder.isEmpty()) {
            log.warn("No matching order: id={}, amount={}, status=PENDING. SepayTransactionId={}",
                    orderId, webhook.getTransferAmount(), webhook.getId());
            return;
        }

        Order order = optionalOrder.get();

        // Double-check status (chỉ update nếu chưa PAID)
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            log.info("Order {} already PAID, skipping. SepayTransactionId={}", orderId, webhook.getId());
            return;
        }

        // Update
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setStatus(OrderStatus.PROCESSING);  // Hoặc PENDING nếu COD, nhưng SEPAY → PROCESSING
        orderRepository.save(order);
        log.info("SUCCESS: Updated order {} to PAID/PROCESSING. SepayTransactionId={}", orderId, webhook.getId());
    }
}