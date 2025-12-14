package com.sprotshop.sportstore.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductStatsDto {
    private Long id;
    private String name;
    private String imageUrl;
    private BigDecimal price;
    private Double averageRating;
    private Integer reviewCount;
    private String brandName;
    private Long totalSold;           // Số lượng đã bán
    private BigDecimal totalRevenue;  // Tổng doanh thu
    private String slug;              // Thêm slug nếu cần (có thể null)

    // Constructor đầy đủ
    public ProductStatsDto(Long id, String name, String imageUrl, BigDecimal price,
                           Double averageRating, Integer reviewCount, String brandName,
                           String slug, Long totalSold, BigDecimal totalRevenue) {
        this.id = id;
        this.name = name;
        this.imageUrl = imageUrl;
        this.price = price;
        this.averageRating = averageRating;
        this.reviewCount = reviewCount;
        this.brandName = brandName;
        this.slug = slug;
        this.totalSold = totalSold;
        this.totalRevenue = totalRevenue;
    }

    // Constructor không có slug
    public ProductStatsDto(Long id, String name, String imageUrl, BigDecimal price,
                           Double averageRating, Integer reviewCount, String brandName,
                           Long totalSold, BigDecimal totalRevenue) {
        this(id, name, imageUrl, price, averageRating, reviewCount, brandName,
                null, totalSold, totalRevenue);
    }
}