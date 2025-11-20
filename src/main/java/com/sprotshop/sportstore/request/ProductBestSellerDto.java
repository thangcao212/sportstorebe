// src/main/java/com/sprotshop/sportstore/dto/ProductBestSellerDto.java
package com.sprotshop.sportstore.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductBestSellerDto {
    private Long productId;
    private String productName;
    private String imageUrl;        // ảnh chính của sản phẩm
    private BigDecimal price;
    private Long totalQuantitySold; // tổng số lượng đã bán trong 30 ngày
}