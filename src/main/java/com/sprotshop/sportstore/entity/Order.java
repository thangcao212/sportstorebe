package com.sprotshop.sportstore.entity;

import com.sprotshop.sportstore.Enum.CouponType;
import com.sprotshop.sportstore.Enum.OrderStatus;
import com.sprotshop.sportstore.Enum.PaymentMethod;
import com.sprotshop.sportstore.Enum.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "shipping_recipient_name", length = 255)
    private String shippingRecipientName;

    @Column(name = "shipping_phone", length = 20)
    private String shippingPhone;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id", nullable = false)
    private Address address;

    @Column(name = "delivery_address", length = 500)
    private String deliveryAddress;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    @Column(name = "total_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "original_total_amount", precision = 15, scale = 2)
    private BigDecimal originalTotalAmount;

    @Column(name = "discount_amount", precision = 15, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "shipping_fee", precision = 15, scale = 2, nullable = false)
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false)
    private OrderStatus orderStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus;

    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at")
    @CreationTimestamp
    private LocalDateTime createdAt;

    // 👈 SINGLE: Keep @ManyToOne for single coupon
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id")
    private Coupon coupon;

    // Helper to calculate total (single coupon)
    public BigDecimal getTotalAmount() {
        BigDecimal itemsTotal = orderItems.stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal discount = BigDecimal.ZERO;
        if (coupon != null) {
            if (coupon.getType() == CouponType.FIXED) {
                discount = (coupon.getDiscountAmount() != null ? coupon.getDiscountAmount() : BigDecimal.ZERO);
            } else {
                BigDecimal percentage = (coupon.getDiscountPercentage() != null ? coupon.getDiscountPercentage() : BigDecimal.ZERO);
                discount = itemsTotal.multiply(percentage.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
            }
        }

        return itemsTotal.add(shippingFee).subtract(discount);
    }

    // Helper for single coupon
    public void applyCoupon(Coupon coupon) {
        this.coupon = coupon;
        if (coupon != null && !coupon.getOrders().contains(this)) {
            coupon.getOrders().add(this);
        }
    }

    public void syncStatuses() {
        this.orderStatus = this.status;
    }
}