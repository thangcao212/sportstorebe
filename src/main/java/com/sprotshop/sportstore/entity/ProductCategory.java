package com.sprotshop.sportstore.entity; // Thay đổi package nếu cần

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Entity đại diện cho Danh mục sản phẩm (Product Category).
 * Hỗ trợ cấu trúc phân cấp cha-con.
 * Tên danh mục (name) được lưu dưới dạng chữ thường (lowercase).
 */
@Entity
@Table(name = "product_category")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name; // Lưu trữ tên đã chuẩn hóa (lowercase)

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ProductCategory parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ProductCategory> children = new ArrayList<>();

    // Cascade được giới hạn để không xóa Product khi xóa Category
    @OneToMany(mappedBy = "productCategory",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH},
            fetch = FetchType.LAZY)
    @Builder.Default
    private List<Product> products = new ArrayList<>();

    // --- Helper Methods ---
    public void addChild(ProductCategory child) {
        if (child != null && !this.children.contains(child)) {
            this.children.add(child);
            child.setParent(this);
        }
    }

    public void removeChild(ProductCategory child) {
        if (child != null && this.children.contains(child)) {
            this.children.remove(child);
            child.setParent(null);
        }
    }

    // --- equals() & hashCode() ---
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductCategory that = (ProductCategory) o;
        return this.id != null && Objects.equals(this.id, that.id);
    }

    @Override
    public int hashCode() {
        return this.id != null ? Objects.hash(this.id) : super.hashCode();
    }

    // --- toString() ---
    @Override
    public String toString() {
        return "ProductCategory{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + (description != null ? description.substring(0, Math.min(description.length(), 50)) + "..." : "null") + '\'' +
                ", parentId=" + (parent != null ? parent.getId() : "null") +
                '}';
    }
}
