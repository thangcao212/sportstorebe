package com.sprotshop.sportstore.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.math.BigDecimal;
import java.util.*;

@Entity
@Table(name = "product")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stockQuantity;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    //@Fetch(FetchMode.SUBSELECT)
    private Set<Image> images = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ProductCategory productCategory;




    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<ProductSize> productSizes = new HashSet<>();


    public void addImage(Image image) {
        if (image != null) {
            if (this.images == null) this.images = new HashSet<>();
            if (this.images.add(image)) { // Set.add() trả về boolean
                image.setProduct(this);
            }
        }
    }

    public void removeImage(Image image) {
        if (image != null && this.images != null) {
            if (this.images.remove(image)) { // Set.remove() trả về boolean
                image.setProduct(null);
            }
        }
    }

    public void addProductSize(ProductSize productSize) {
        if (productSize != null) {
            if (this.productSizes == null) this.productSizes = new HashSet<>();
            // Ensure productSize is not already associated or handle as needed
            if (!this.productSizes.contains(productSize)) { // Simple check, might need equals/hashCode on ProductSize for this
                this.productSizes.add(productSize);
                productSize.setProduct(this);
            }
        }
    }

    public void removeProductSize(ProductSize productSize) {
        if (productSize != null && this.productSizes != null) {
            this.productSizes.remove(productSize);
            productSize.setProduct(null);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return id != null && Objects.equals(id, product.id);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : super.hashCode();
    }

    @Override
    public String toString() {
        return "Product{id=" + id + ", name='" + name + '\'' + ", price=" + price + ", stockQuantity=" + stockQuantity + "}";
    }

    // Thêm Brand
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    // Thêm Reviews (để lấy đánh giá sản phẩm)
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Review> reviews = new ArrayList<>();

    // Helper methods cho Brand
    public void setBrand(Brand brand) {
        this.brand = brand;
        if (brand != null && !brand.getProducts().contains(this)) {
            brand.getProducts().add(this);
        }
    }

    // Helper methods cho Review
    public void addReview(Review review) {
        if (review != null) {
            if (this.reviews == null) this.reviews = new ArrayList<>();
            if (!this.reviews.contains(review)) {
                this.reviews.add(review);
                review.setProduct(this);
            }
        }
    }

    public void removeReview(Review review) {
        if (review != null && this.reviews != null) {
            this.reviews.remove(review);
            review.setProduct(null);
        }
    }


}