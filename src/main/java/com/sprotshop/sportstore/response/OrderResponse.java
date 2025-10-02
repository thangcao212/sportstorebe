// Updated OrderResponse.java - Added setter for bankInfo (since it's used in createOrderFromCart)
package com.sprotshop.sportstore.response;

import com.sprotshop.sportstore.Enum.OrderStatus;
import com.sprotshop.sportstore.Enum.PaymentMethod;
import com.sprotshop.sportstore.entity.Order;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
    private String recipientName;
    private String phone;
    private String street;
    private String fullAddress;
    private List<OrderItemDTO> items;
    private String qrCodeUrl; // For SEPAY
    private Map<String, Object> bankInfo;

    // Setter for bankInfo
    public void setBankInfo(Map<String, Object> bankInfo) {
        this.bankInfo = bankInfo;
    }

    public static OrderResponse fromEntity(Order order) {
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
                .paymentStatus(order.getPaymentStatus().name())
                .trackingNumber(order.getTrackingNumber())
                .recipientName(order.getShippingRecipientName())
                .phone(order.getShippingPhone())
                .street(order.getAddress().getStreet())
                .fullAddress(order.getAddress().getFullAddress())
                .items(order.getOrderItems() != null
                        ? order.getOrderItems().stream()
                        .map(item -> OrderItemDTO.builder()
                                .orderItemId(item.getId())
                                .productId(item.getProduct().getId())
                                .productName(item.getProduct().getName())
                                .quantity(item.getQuantity())
                                .price(item.getPrice())
                                .size(item.getSize())
                                .productImageUrl(item.getProduct().getImages().stream().findFirst()
                                        .map(image -> image.getImageUrl()).orElse(null))
                                .build())
                        .toList()
                        : Collections.emptyList())
                .build();
    }
}