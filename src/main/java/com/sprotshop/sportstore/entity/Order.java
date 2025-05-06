package com.sprotshop.sportstore.entity; // Thay đổi package nếu cần

import com.sprotshop.sportstore.Enum.OrderStatus;
import com.sprotshop.sportstore.Enum.PaymentMethod;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal; // Dùng BigDecimal cho tiền tệ
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Entity đại diện cho một Đơn hàng của khách hàng.
 */
@Entity
// Dùng dấu nháy ngược vì ORDER là từ khóa SQL có thể gây lỗi ở một số DB
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Người dùng đặt hàng (bắt buộc)
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // Đảm bảo bạn có User entity

    // Danh sách các mục trong đơn hàng
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrderItem> orderItems = new ArrayList<>();

    // --- Thông tin giao hàng (Sao chép tại thời điểm đặt hàng) ---
    @NotBlank(message = "Tên người nhận không được trống")
    @Column(nullable = false)
    private String shippingRecipientName;

    @NotBlank(message = "Số điện thoại người nhận không được trống")
    @Column(nullable = false, length = 15)
    private String shippingPhone;

    @NotBlank(message = "Địa chỉ đường không được trống")
    @Column(nullable = false)
    private String shippingStreet;

    @NotBlank(message = "Phường/Xã không được trống")
    @Column(nullable = false)
    private String shippingWard;

    @NotBlank(message = "Quận/Huyện không được trống")
    @Column(nullable = false)
    private String shippingDistrict;

    @NotBlank(message = "Tỉnh/Thành phố không được trống")
    @Column(nullable = false)
    private String shippingCity;
    // ---------------------------------------------------------

    // Tổng tiền cuối cùng của đơn hàng
    @NotNull(message = "Tổng tiền đơn hàng không được trống")
    @Column(nullable = false, precision = 15, scale = 2) // precision/scale cho tiền tệ
    private BigDecimal totalAmount; // Dùng BigDecimal

    // Trạng thái đơn hàng
    @NotNull
    @Enumerated(EnumType.STRING) // Lưu tên Enum (PENDING, PROCESSING...)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING; // Trạng thái mặc định

    @Column(columnDefinition = "TEXT")
    private String notes; // Ghi chú của khách (tùy chọn)

    @NotNull(message = "Phương thức thanh toán không được trống")
    @Enumerated(EnumType.STRING) // << Lưu tên Enum (COD, VNPAY...) vào DB
    @Column(name = "payment_method", length = 50, nullable = false) // << Đổi kiểu cột nếu cần, thêm nullable=false
    private PaymentMethod paymentMethod;

    @Column(length = 50)
    @Builder.Default
    private String paymentStatus = "PENDING"; // Ví dụ: PENDING, PAID, FAILED

    @Column(length = 100)
    private String trackingNumber; // Mã vận đơn (tùy chọn)

    @CreationTimestamp // Tự động gán khi tạo bởi Hibernate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp // Tự động gán khi cập nhật bởi Hibernate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // --- Helper Methods ---
    public void addOrderItem(OrderItem item) {
        if (item != null) {
            if (this.orderItems == null) this.orderItems = new ArrayList<>();
            if (!this.orderItems.contains(item)) this.orderItems.add(item);
            item.setOrder(this);
        }
    }
    public void removeOrderItem(OrderItem item) {
        if (item != null && this.orderItems != null) {
            this.orderItems.remove(item);
            item.setOrder(null);
        }
    }

    // --- equals() & hashCode() ---
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        return id != null && Objects.equals(id, order.id);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : super.hashCode();
    }
}