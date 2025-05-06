package com.sprotshop.sportstore.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;

@Entity
@Table(name = "cart")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class) // Cho @CreatedDate hoạt động
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Cart trong DB luôn thuộc về 1 User (nullable = false)
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // Đảm bảo bạn có User entity

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<CartItem> cartItems = new HashSet<>();

    @CreationTimestamp
    @Temporal(TemporalType.TIMESTAMP)
     private  Date createdAt;

     @UpdateTimestamp
     @Temporal(TemporalType.TIMESTAMP)
     private Date updatedAt;

    // --- Helper Methods ---
    public void addCartItem(CartItem item) {
        if (item != null) {
            if (this.cartItems == null) this.cartItems = new HashSet<>();
            this.cartItems.add(item);
            item.setCart(this);
        }
    }
    public void removeCartItem(CartItem item) {
        if (item != null && this.cartItems != null) {

            this.cartItems.remove(item);
            item.setCart(null);
        }
    }

    // --- equals() & hashCode() ---
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Cart cart = (Cart) o;
        return id != null && id.equals(cart.id);
    }
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : super.hashCode();
    }
}