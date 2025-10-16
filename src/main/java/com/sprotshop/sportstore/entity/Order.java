
        package com.sprotshop.sportstore.entity;

import com.sprotshop.sportstore.Enum.CouponType;
import com.sprotshop.sportstore.Enum.OrderStatus;
import com.sprotshop.sportstore.Enum.PaymentMethod;
import com.sprotshop.sportstore.Enum.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    private String shippingRecipientName;
    private String shippingPhone;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "address_id")
    private Address address;

    // 👈 NEW: Snapshot of full address at order creation (immutable for history)
    @Column(name = "delivery_address", length = 500)
    private String deliveryAddress;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<OrderItem> orderItems = new ArrayList<>();

    private BigDecimal totalAmount;
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Column(name = "payment_method")
    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @Column(name = "payment_status")
    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    @Column(name="order_status")
    @Enumerated(EnumType.STRING)
    private OrderStatus orderStatus;

    private String trackingNumber;
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Thêm Coupon
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id")
    private Coupon coupon;

    // ShippingFee (đã có từ trước, nhưng cập nhật helper cho free ship)
    @Column(name = "shipping_fee", precision = 15, scale = 2)
    private BigDecimal shippingFee = BigDecimal.ZERO;  // Default 0, tính ở service

    // Helper method tính total (cập nhật để include coupon & ship)
    public BigDecimal getTotalAmount() {
        BigDecimal itemsTotal = orderItems.stream()
                .map(item -> {
                    BigDecimal price = (item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO);
                    return price.multiply(BigDecimal.valueOf(item.getQuantity()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal discount = BigDecimal.ZERO;
        if (coupon != null) {
            if (coupon.getType() == CouponType.FIXED) {
                discount = (coupon.getDiscountAmount() != null ? coupon.getDiscountAmount() : BigDecimal.ZERO);
            } else {
                BigDecimal percentage = (coupon.getDiscountPercentage() != null ? coupon.getDiscountPercentage() : BigDecimal.ZERO);
                discount = itemsTotal.multiply(percentage.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));  // Thêm scale/rounding để an toàn
            }
        }

        BigDecimal shipFee = (shippingFee != null ? shippingFee : BigDecimal.ZERO);
        return itemsTotal.add(shipFee).subtract(discount);
    }

    // Helper để apply coupon
    public void applyCoupon(Coupon coupon) {
        this.coupon = coupon;
        if (coupon != null && !coupon.getOrders().contains(this)) {
            coupon.getOrders().add(this);
        }
    }
}