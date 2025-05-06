package com.sprotshop.sportstore.entity; // Thay đổi package nếu cần

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal; // Import BigDecimal
import java.util.Objects;

/**
 * Entity đại diện cho một mục (sản phẩm cụ thể) trong một Đơn hàng.
 * Lưu trữ thông tin sản phẩm, số lượng và giá tại thời điểm đặt hàng.
 */
@Entity
@Table(name = "order_item", uniqueConstraints = {
        // Đảm bảo không có 2 dòng cho cùng 1 sản phẩm trong cùng 1 đơn hàng
        @UniqueConstraint(columnNames = {"order_id", "product_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Liên kết đến Order cha (bắt buộc)
    @NotNull // Thêm validation nếu cần
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // Liên kết đến Product (bắt buộc, giả sử product không bị xóa cứng)
    @NotNull // Thêm validation nếu cần
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // Số lượng sản phẩm này trong đơn hàng
    @NotNull(message = "Số lượng không được trống")
    @Min(value = 1, message = "Số lượng phải lớn hơn hoặc bằng 1")
    @Column(nullable = false)
    private Integer quantity;

    /**
     * Giá của sản phẩm TẠI THỜI ĐIỂM đặt hàng (Rất quan trọng).
     * Nên dùng BigDecimal.
     */
    @NotNull(message = "Giá tại thời điểm đặt hàng không được trống")
    @Column(name = "price_at_order", nullable = false, precision = 15, scale = 2)
    private BigDecimal price; // Dùng BigDecimal

    // --- equals() & hashCode() ---
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderItem orderItem = (OrderItem) o;
        // So sánh bằng ID nếu đã có
        if (id != null && orderItem.id != null) {
            return Objects.equals(id, orderItem.id);
        }
        // Nếu chưa có ID, so sánh dựa trên Order và Product
        // (Hữu ích khi dùng trong Set trước khi persist)
        return Objects.equals(order, orderItem.order) &&
                Objects.equals(product, orderItem.product);
    }

    @Override
    public int hashCode() {
        // Dùng ID nếu có, nếu không dùng Order và Product
        return id != null ? Objects.hash(id) : Objects.hash(order, product);
    }
}