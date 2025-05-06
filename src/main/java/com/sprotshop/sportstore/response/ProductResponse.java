package com.sprotshop.sportstore.response;

import com.sprotshop.sportstore.entity.Image;
import com.sprotshop.sportstore.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.Hibernate;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {
    private Long id;
    private String name;
    private String description;
    private Double price;
    private Integer stockQuantity;
    private List<ImageInfo> images;
    private Long categoryId;
    private String categoryName;
    private String categoryDescription;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageInfo {
        private Long id;
        private String imageId;
        private String imageUrl;
        private String name;
    }

    public static ProductResponse fromEntity(Product product) {
        if (product == null) return null;
        List<ImageInfo> imageInfos = Collections.emptyList();
        if (product.getImages() != null && Hibernate.isInitialized(product.getImages())) {
            if (!product.getImages().isEmpty()) {
                imageInfos = product.getImages().stream().map(image -> ImageInfo.builder()
                        .id(image.getId()).imageId(image.getImageId()).imageUrl(image.getImageUrl()).name(image.getName())
                        .build()).collect(Collectors.toList());
            }
        }
        Long catId = null; String catName = null; String catDesc = null;
        if (product.getProductCategory() != null && Hibernate.isInitialized(product.getProductCategory())) {
            catId = product.getProductCategory().getId();
            catName = product.getProductCategory().getName();
            catDesc = product.getProductCategory().getDescription();
        }
        return ProductResponse.builder()
                .id(product.getId()).name(product.getName()).description(product.getDescription())
                .price(product.getPrice()).stockQuantity(product.getStockQuantity()).images(imageInfos)
                .categoryId(catId).categoryName(catName).categoryDescription(catDesc)
                .build();
    }
    public static List<ProductResponse> fromEntities(List<Product> products) {
        if (products == null || products.isEmpty()) return Collections.emptyList();
        return products.stream().map(ProductResponse::fromEntity).collect(Collectors.toList());
    }
}