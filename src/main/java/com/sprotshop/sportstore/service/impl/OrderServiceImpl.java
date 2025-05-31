
        package com.sprotshop.sportstore.service.impl;

import com.sprotshop.sportstore.Enum.OrderStatus;
import com.sprotshop.sportstore.Enum.PaymentMethod;
import com.sprotshop.sportstore.entity.*;
import com.sprotshop.sportstore.exception.NotFoundException;
import com.sprotshop.sportstore.repository.*;
import com.sprotshop.sportstore.request.CreateOrderRequest;
import com.sprotshop.sportstore.request.OrderSearchRequest;
import com.sprotshop.sportstore.response.DistrictDTO;
import com.sprotshop.sportstore.response.OrderResponse;
import com.sprotshop.sportstore.response.ProvinceDTO;
import com.sprotshop.sportstore.response.WardDTO;
import com.sprotshop.sportstore.service.CartService;
import com.sprotshop.sportstore.service.LocationService;
import com.sprotshop.sportstore.service.OrderService;
import com.sprotshop.sportstore.service.UserService;
import com.sprotshop.sportstore.utils.OrderSpecification;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import jakarta.persistence.criteria.JoinType;
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
    private final LocationService locationService;

    @Override
    @Transactional
    public OrderResponse createOrderFromCart(CreateOrderRequest request) {
        User currentUser = userService.getCurrentLoggedInUser();
        Long userId = currentUser.getId();
        log.info("Creating order for userId: {}", userId);

        // Validate location codes
        ProvinceDTO province = locationService.getAllProvinces().stream()
                .filter(p -> p.getCode().equals(request.getProvinceCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid province code: " + request.getProvinceCode()));

        DistrictDTO district = locationService.getDistrictsByProvinceCode(request.getProvinceCode()).stream()
                .filter(d -> d.getCode().equals(request.getDistrictCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid district code: " + request.getDistrictCode()));

        WardDTO ward = locationService.getWardsByDistrictCode(request.getDistrictCode()).stream()
                .filter(w -> w.getCode().equals(request.getWardCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid ward code: " + request.getWardCode()));

        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Cart not found for user ID: " + userId));

        if (CollectionUtils.isEmpty(cart.getCartItems())) {
            throw new IllegalStateException("Cart is empty");
        }

        Map<Product, Integer> productQuantityMap = new HashMap<>();
        Map<Product, BigDecimal> priceAtOrderMap = new HashMap<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        Map<Long, Integer> stockUpdates = new HashMap<>();

        for (CartItem cartItem : cart.getCartItems()) {
            Product product = cartItem.getProduct();
            int quantity = cartItem.getQuantity();
            if (product == null || product.getStockQuantity() < quantity) {
                throw new IllegalArgumentException("Product out of stock: " + (product != null ? product.getName() : "null"));
            }
            BigDecimal price = BigDecimal.valueOf(product.getPrice());
            productQuantityMap.put(product, quantity);
            priceAtOrderMap.put(product, price);
            totalAmount = totalAmount.add(price.multiply(BigDecimal.valueOf(quantity)));
            stockUpdates.put(product.getId(), quantity);
        }

        Order order = Order.builder()
                .user(currentUser)
                .shippingRecipientName(request.getShippingRecipientName())
                .shippingPhone(request.getShippingPhone())
                .shippingStreet(request.getShippingStreet())
                .shippingWard(ward.getName())
                .shippingDistrict(district.getName())
                .shippingCity(province.getName())
                .totalAmount(totalAmount)
                .status(OrderStatus.PENDING)
                .notes(request.getNotes())
                .paymentMethod(request.getPaymentMethod())
                .paymentStatus("PENDING")
                .orderItems(new ArrayList<>()) // Khởi tạo rõ ràng
                .build();
        Order savedOrder = orderRepository.save(order);

        List<OrderItem> orderItems = new ArrayList<>();
        for (Map.Entry<Product, Integer> entry : productQuantityMap.entrySet()) {
            OrderItem item = OrderItem.builder()
                    .order(savedOrder)
                    .product(entry.getKey())
                    .quantity(entry.getValue())
                    .price(priceAtOrderMap.get(entry.getKey()))
                    .build();
            orderItems.add(item);
            orderItemRepository.save(item);
        }
        savedOrder.setOrderItems(orderItems);

        stockUpdates.forEach((productId, quantity) -> {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new NotFoundException("Product not found: " + productId));
            product.setStockQuantity(product.getStockQuantity() - quantity);
            productRepository.save(product);
        });

        cartService.clearCart();
        Hibernate.initialize(savedOrder.getOrderItems()); // Đảm bảo orderItems được tải
        return OrderResponse.fromEntity(orderRepository.findById(savedOrder.getId()).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "userOrders", key = "#pageable.pageNumber + '-' + #pageable.pageSize")
    public Page<OrderResponse> getUserOrders(Pageable pageable) {
        User currentUser = userService.getCurrentLoggedInUser();
        Page<Order> orders = orderRepository.findByUserId(currentUser.getId(), pageable);
        orders.forEach(order -> Hibernate.initialize(order.getOrderItems())); // Tải orderItems
        return orders.map(OrderResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderDetails(Long orderId) {
        User currentUser = userService.getCurrentLoggedInUser();
        Order order = orderRepository.findByIdAndUserId(orderId, currentUser.getId())
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));
        Hibernate.initialize(order.getOrderItems()); // Tải orderItems
        return OrderResponse.fromEntity(order);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long orderId) {
        User currentUser = userService.getCurrentLoggedInUser();
        Order order = orderRepository.findByIdAndUserId(orderId, currentUser.getId())
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));
        if (!List.of(OrderStatus.PENDING, OrderStatus.WAITING_FOR_PAYMENT, OrderStatus.PROCESSING).contains(order.getStatus())) {
            throw new IllegalStateException("Cannot cancel order in state: " + order.getStatus());
        }
        Hibernate.initialize(order.getOrderItems()); // Tải orderItems
        if (!order.getOrderItems().isEmpty()) {
            order.getOrderItems().forEach(item -> {
                Product product = item.getProduct();
                product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
                productRepository.save(product);
            });
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
        Hibernate.initialize(order.getOrderItems()); // Tải orderItems
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
        Hibernate.initialize(order.getOrderItems()); // Tải orderItems
        return OrderResponse.fromEntity(orderRepository.save(order));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderByIdForAdmin(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));
        Hibernate.initialize(order.getOrderItems()); // Tải orderItems
        return OrderResponse.fromEntity(order);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "allOrders", key = "#pageable.pageNumber + '-' + #pageable.pageSize")
    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        Page<Order> orders = orderRepository.findAll(pageable);
        orders.forEach(order -> Hibernate.initialize(order.getOrderItems())); // Tải orderItems
        return orders.map(OrderResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> searchOrders(OrderSearchRequest searchRequest, Pageable pageable) {
        log.info("Searching orders with request: {}", searchRequest);
        Specification<Order> spec = OrderSpecification.buildSearchSpecification(searchRequest);
        Specification<Order> finalSpec = spec;
        spec = (root, query, cb) -> {
            query.distinct(true);
            root.fetch("user", JoinType.LEFT);
            root.fetch("orderItems", JoinType.LEFT);
            return finalSpec.toPredicate(root, query, cb);
        };
        Page<Order> orders = orderRepository.findAll(spec, pageable);
        log.info("Found {} orders", orders.getTotalElements());
        orders.forEach(order -> Hibernate.initialize(order.getOrderItems()));
        return orders.map(OrderResponse::fromEntity);
    }
}
