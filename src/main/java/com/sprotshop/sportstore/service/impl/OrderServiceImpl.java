package com.sprotshop.sportstore.service.impl;

import com.sprotshop.sportstore.Enum.OrderStatus;
import com.sprotshop.sportstore.Enum.PaymentMethod;
import com.sprotshop.sportstore.entity.*;
import com.sprotshop.sportstore.exception.NotFoundException;
import com.sprotshop.sportstore.repository.*;
import com.sprotshop.sportstore.request.CreateOrderRequest;
import com.sprotshop.sportstore.request.OrderSearchRequest;
import com.sprotshop.sportstore.request.SearchOrderRequest;
import com.sprotshop.sportstore.response.OrderResponse;
import com.sprotshop.sportstore.response.PageResponse;
import com.sprotshop.sportstore.service.CartService;
import com.sprotshop.sportstore.service.OrderService;
import com.sprotshop.sportstore.service.UserService;
import com.sprotshop.sportstore.utils.OrderSpecification;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final CacheManager cacheManager;

    @Override
    @Transactional
    @CacheEvict(value = {"userOrders", "allOrders"}, allEntries = true)
    public OrderResponse createOrderFromCart(CreateOrderRequest request) {
        User currentUser = userService.getCurrentLoggedInUser();
        Long userId = currentUser.getId();
        log.info("Creating order for userId: {}", userId);

        // Validate location codes
        Province province = provinceRepository.findById(request.getProvinceCode())
                .orElseThrow(() -> new IllegalArgumentException("Invalid province code: " + request.getProvinceCode()));
        District district = districtRepository.findById(request.getDistrictCode())
                .orElseThrow(() -> new IllegalArgumentException("Invalid district code: " + request.getDistrictCode()));
        Ward ward = wardRepository.findById(request.getWardCode())
                .orElseThrow(() -> new IllegalArgumentException("Invalid ward code: " + request.getWardCode()));

        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Cart not found for user ID: " + userId));

        if (CollectionUtils.isEmpty(cart.getCartItems())) {
            throw new IllegalStateException("Cart is empty");
        }

        Map<String, Integer> productSizeQuantityMap = new HashMap<>();
        Map<String, BigDecimal> priceAtOrderMap = new HashMap<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        Map<String, Integer> stockUpdates = new HashMap<>();

        for (CartItem cartItem : cart.getCartItems()) {
            Product product = cartItem.getProduct();
            String size = cartItem.getSize();
            int quantity = cartItem.getQuantity();
            String productSizeKey = product.getId() + "-" + size; // Key: productId-size

            ProductSize variant = productSizeRepository.findByProductIdAndSize(product.getId(), size)
                    .orElseThrow(() -> new NotFoundException("Product size not found for productId: " + product.getId() + ", size: " + size));
            if (variant.getStockQuantity() < quantity) {
                throw new IllegalArgumentException("Product out of stock for size " + size + ": " + product.getName());
            }

            BigDecimal price = BigDecimal.valueOf(product.getPrice());
            productSizeQuantityMap.put(productSizeKey, quantity);
            priceAtOrderMap.put(productSizeKey, price);
            totalAmount = totalAmount.add(price.multiply(BigDecimal.valueOf(quantity)));
            stockUpdates.put(productSizeKey, quantity);
        }

        // Create Address
        String fullAddress = String.format("%s, %s, %s, %s", request.getStreet(), ward.getName(), district.getName(), province.getName());
        Address address = Address.builder()
                .provinceCode(request.getProvinceCode())
                .districtCode(request.getDistrictCode())
                .wardCode(request.getWardCode())
                .street(request.getStreet())
                .fullAddress(fullAddress)
                .build();
        addressRepository.save(address);

        Order order = Order.builder()
                .user(currentUser)
                .address(address)
                .totalAmount(totalAmount)
                .status(OrderStatus.PENDING)
                .paymentMethod(request.getPaymentMethod())
                .paymentStatus("PENDING")
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
                    .orElseThrow(() -> new NotFoundException("Product not found: " + productId));

            OrderItem item = OrderItem.builder()
                    .order(savedOrder)
                    .product(product)
                    .quantity(entry.getValue())
                    .price(priceAtOrderMap.get(entry.getKey()))
                    .size(size)
                    .build();
            orderItems.add(item);
            orderItemRepository.save(item);
        }
        savedOrder.setOrderItems(orderItems);

        stockUpdates.forEach((productSizeKey, quantity) -> {
            String[] parts = productSizeKey.split("-");
            Long productId = Long.parseLong(parts[0]);
            String size = parts[1];
            ProductSize variant = productSizeRepository.findByProductIdAndSize(productId, size)
                    .orElseThrow(() -> new NotFoundException("Product size not found for productId: " + productId + ", size: " + size));
            variant.setStockQuantity(variant.getStockQuantity() - quantity);
            productSizeRepository.save(variant);
        });

        cartService.clearCart();
        Hibernate.initialize(savedOrder.getOrderItems());
        return OrderResponse.fromEntity(orderRepository.findById(savedOrder.getId()).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderDetails(Long orderId) {
        User currentUser = userService.getCurrentLoggedInUser();
        Order order = orderRepository.findByIdAndUserId(orderId, currentUser.getId())
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));
        Hibernate.initialize(order.getOrderItems());
        return OrderResponse.fromEntity(order);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long orderId) {
        User currentUser = userService.getCurrentLoggedInUser();
        Order order = orderRepository.findByIdAndUserId(orderId, currentUser.getId())
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));

        if (!List.of(OrderStatus.PENDING, OrderStatus.WAITING_FOR_PAYMENT, OrderStatus.PROCESSING)
                .contains(order.getStatus())) {
            throw new IllegalStateException("Cannot cancel order in state: " + order.getStatus());
        }

        Hibernate.initialize(order.getOrderItems());

        if (!order.getOrderItems().isEmpty()) {
            List<ProductSize> variantsToUpdate = new ArrayList<>();

            order.getOrderItems().forEach(item -> {
                ProductSize variant = productSizeRepository.findByProductIdAndSize(
                        item.getProduct().getId(),
                        item.getSize()
                ).orElseThrow(() -> new NotFoundException(
                        "ProductSize not found for productId " + item.getProduct().getId() +
                                " and size " + item.getSize()
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
    public OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));
        order.setStatus(newStatus);
        Hibernate.initialize(order.getOrderItems());
        return OrderResponse.fromEntity(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderResponse addTrackingNumber(Long orderId, String trackingNumber) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));
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
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));
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
        log.info("Searching orders with request: {}", request);
        Specification<Order> spec = OrderSpecification.filterOrders(request);
        Page<Order> orders = orderRepository.findAll(spec, pageable);
        log.info("Found {} orders", orders.getTotalElements());
        orders.forEach(order -> Hibernate.initialize(order.getOrderItems()));
        return PageResponse.fromPage(orders.map(OrderResponse::fromEntity));
    }
}