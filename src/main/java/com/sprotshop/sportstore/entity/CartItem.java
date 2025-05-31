package com.sprotshop.sportstore.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.Objects;

@Entity
@Table(name = "cart_item", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"cart_id", "product_id", "size"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    @JsonIgnore // Tránh vòng lặp khi serialize
    private Cart cart;

    // Liên kết EAGER đến Product để tiện lấy thông tin giá, tên khi tính toán/map DTO
    // Cân nhắc dùng LAZY + @EntityGraph nếu có nhiều CartItem
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;


    @NotNull(message = "Số lượng không được để trống")
    @Min(value = 1, message = "Số lượng phải lớn hơn hoặc bằng 1")
    @Column(nullable = false)
    private Integer quantity;

    private String size;

    // --- equals() & hashCode() ---
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CartItem cartItem = (CartItem) o;
        if (id != null && cartItem.id != null) { // So sánh bằng ID nếu có
            return id.equals(cartItem.id);
        }
        // Nếu chưa có ID, so sánh bằng cart và product (hữu ích khi check trùng trong Set)
        return Objects.equals(cart, cartItem.cart) &&
                Objects.equals(product, cartItem.product);
    }

    @Override
    public int hashCode() {
        // Dùng hash của ID nếu có, nếu không dùng cart & product
        return id != null ? Objects.hash(id) : Objects.hash(cart, product);
    }
}