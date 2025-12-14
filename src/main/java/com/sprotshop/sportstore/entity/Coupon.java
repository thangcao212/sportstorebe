// Coupon.java (Entity - Update nullable columns)
package com.sprotshop.sportstore.entity;

import com.sprotshop.sportstore.Enum.CouponScope;
import com.sprotshop.sportstore.Enum.CouponType;  // Assuming this is the enum
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "coupons")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Coupon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code;

    private String description;

    // 👈 Fix: nullable = true để cho phép null khi type = PERCENTAGE
    @Column(nullable = true, precision = 19, scale = 2)
    private BigDecimal discountAmount;

    // 👈 Fix: nullable = true để cho phép null khi type = FIXED
    @Column(nullable = true, precision = 5, scale = 2)
    private BigDecimal discountPercentage;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal minOrderValue;

    @Column
    private Integer maxUsagePerUser;

    @Column
    private Integer totalUsageLimit;

    @Column(nullable = false)
    private LocalDateTime startDate;

    @Column(nullable = false)
    private LocalDateTime endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponType type;

    @Column(nullable = false)
    private Integer usedCount = 0;

    @OneToMany(mappedBy = "coupon", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Order> orders;  // Assuming relation to Order

    // Nested enum (if not separate)


    // ← MỚI: CAP tiền giảm tối đa (bắt buộc với %)
    @Column(precision = 19, scale = 2)
    private BigDecimal maxDiscountAmount;

    // ← MỚI: Chỉ áp % nếu đơn ≤ giá trị này (tùy chọn, rất an toàn)
    @Column(precision = 19, scale = 2)
    private BigDecimal maxApplicableOrderValue;


    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "coupon_applicable_products",
            joinColumns = @JoinColumn(name = "coupon_id"),
            inverseJoinColumns = @JoinColumn(name = "product_id")
    )
    @Builder.Default
    private Set<Product> applicableProducts = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CouponScope scope = CouponScope.ALL_PRODUCTS;

    // NEW: Nếu scope là CATEGORY hoặc BRAND
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicable_category_id")
    private ProductCategory applicableCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicable_brand_id")
    private Brand applicableBrand;

}