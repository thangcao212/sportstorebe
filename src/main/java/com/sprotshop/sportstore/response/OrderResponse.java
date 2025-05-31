
        package com.sprotshop.sportstore.response;

import com.sprotshop.sportstore.Enum.OrderStatus;
import com.sprotshop.sportstore.Enum.PaymentMethod;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private Long orderId;
    private UserDTO user;
    private LocalDateTime orderDate;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private PaymentMethod paymentMethod;
    private String paymentStatus;
    private String trackingNumber;
    private String notes;
    private String shippingRecipientName;
    private String shippingPhone;
    private String shippingStreet;
    private String shippingWard;
    private String shippingDistrict;
    private String shippingCity;
    private List<OrderItemDTO> items;

    public static OrderResponse fromEntity(com.sprotshop.sportstore.entity.Order order) {
        return OrderResponse.builder()
                .orderId(order.getId())
                .user(UserDTO.builder()
                        .userId(order.getUser().getId())
                        .email(order.getUser().getEmail())

                        .build())
                .orderDate(order.getCreatedAt())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .trackingNumber(order.getTrackingNumber())
                .notes(order.getNotes())
                .shippingRecipientName(order.getShippingRecipientName())
                .shippingPhone(order.getShippingPhone())
                .shippingStreet(order.getShippingStreet())
                .shippingWard(order.getShippingWard())
                .shippingDistrict(order.getShippingDistrict())
                .shippingCity(order.getShippingCity())
                .items(order.getOrderItems() != null
                        ? order.getOrderItems().stream()
                        .map(item -> OrderItemDTO.builder()
                                .orderItemId(item.getId())
                                .productId(item.getProduct().getId())
                                .productName(item.getProduct().getName())
                                .quantity(item.getQuantity())
                                .price(item.getPrice())
                                .size(item.getSize())
                                .productImageUrl(item.getProduct().getImages().stream().findFirst().map(image -> image.getImageUrl()).orElse(null)) // Add image URL    ()) // Add image URL
                                .build())
                        .toList()
                        : Collections.emptyList())
                .build();
    }
}
