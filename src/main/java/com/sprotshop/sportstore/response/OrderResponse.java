package com.sprotshop.sportstore.response;

import com.sprotshop.sportstore.Enum.OrderStatus;
import com.sprotshop.sportstore.Enum.PaymentMethod; // << Import Enum mới
import com.sprotshop.sportstore.entity.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private Long orderId;
    private Long userId;
    private LocalDateTime orderDate;
    private OrderStatus status;
    private BigDecimal totalAmount;

    // --- THAY ĐỔI Ở ĐÂY ---
    private PaymentMethod paymentMethod; // << Đổi thành Enum
    // --- KẾT THÚC THAY ĐỔI ---

    private String paymentStatus;
    private String trackingNumber;
    private String notes;
    private String shippingRecipientName;
    private String shippingPhone;
    private String shippingStreet;
    private String shippingWard;
    private String shippingDistrict;
    private String shippingCity;
    private List<OrderItemResponse> items;

    public static OrderResponse fromEntity(Order order) {
        if (order == null) return null;

        List<OrderItemResponse> itemResponses = Collections.emptyList();
        if (!CollectionUtils.isEmpty(order.getOrderItems()) && Hibernate.isInitialized(order.getOrderItems())) {
            itemResponses = order.getOrderItems().stream()
                    .map(OrderItemResponse::fromEntity)
                    .collect(Collectors.toList());
        }

        return OrderResponse.builder()
                .orderId(order.getId())
                .userId(order.getUser() != null && Hibernate.isInitialized(order.getUser()) ? order.getUser().getId() : null)
                .orderDate(order.getCreatedAt())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .paymentMethod(order.getPaymentMethod()) // << Lấy giá trị Enum
                .paymentStatus(order.getPaymentStatus())
                .trackingNumber(order.getTrackingNumber())
                .notes(order.getNotes())
                .shippingRecipientName(order.getShippingRecipientName())
                .shippingPhone(order.getShippingPhone())
                .shippingStreet(order.getShippingStreet())
                .shippingWard(order.getShippingWard())
                .shippingDistrict(order.getShippingDistrict())
                .shippingCity(order.getShippingCity())
                .items(itemResponses)
                .build();
    }
}