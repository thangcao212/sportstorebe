// Updated OrderResponse.java - Added shippingFee field for response
// (Add to existing OrderResponse class)

package com.sprotshop.sportstore.response;

import com.sprotshop.sportstore.Enum.OrderStatus;
import com.sprotshop.sportstore.Enum.PaymentMethod;
import com.sprotshop.sportstore.entity.Coupon;
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
    private BigDecimal totalAmount;  // After discount
    private BigDecimal originalTotalAmount;  // NEW: Before discount
    private BigDecimal discountAmount;  // NEW: Discount applied
    private String couponCode;  // NEW: Coupon code used
    private BigDecimal shippingFee;  // NEW: Shipping fee
    private PaymentMethod paymentMethod;
    private String paymentStatus;
    private String trackingNumber;
    private String recipientName;
    private String phone;
    private String label;  // From address (if needed)
    private String street; // From address (if needed)
    private String fullAddress;  // Legacy, or keep for backward compat
    private String deliveryAddress;
    private List<OrderItemDTO> items;
    private String qrCodeUrl; // For SEPAY
    private Map<String, Object> bankInfo;


    // Setter for bankInfo
    public void setBankInfo(Map<String, Object> bankInfo) {
        this.bankInfo = bankInfo;
    }

    // NEW: Setters for additional fields
    public void setOriginalTotalAmount(BigDecimal originalTotalAmount) {
        this.originalTotalAmount = originalTotalAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public void setCouponCode(String couponCode) {
        this.couponCode = couponCode;
    }

    public void setShippingFee(BigDecimal shippingFee) {
        this.shippingFee = shippingFee;
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
                .originalTotalAmount(order.getOriginalTotalAmount())  // 👈 NEW: From entity
                .discountAmount(order.getDiscountAmount())  // 👈 NEW: From entity
                .shippingFee(order.getShippingFee())  // 👈 NEW: Set from entity
                .couponCode(order.getCoupon() != null ? order.getCoupon().getCode() : null)  // Hoặc "" nếu muốn empty string
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus().name())
                .trackingNumber(order.getTrackingNumber())
                .recipientName(order.getShippingRecipientName())
                .deliveryAddress(order.getDeliveryAddress())
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