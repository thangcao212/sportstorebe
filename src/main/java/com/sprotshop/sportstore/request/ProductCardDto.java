package com.sprotshop.sportstore.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductCardDto {
    private Long id;
    private String name;
    private String imageUrl;
    private BigDecimal price;
    private Double averageRating;
    private Integer reviewCount;
    private String brandName;
    private String slug;
}