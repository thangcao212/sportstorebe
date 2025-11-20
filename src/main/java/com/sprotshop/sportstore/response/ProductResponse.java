package com.sprotshop.sportstore.response;

import com.sprotshop.sportstore.entity.Brand;
import com.sprotshop.sportstore.entity.Image;
import com.sprotshop.sportstore.entity.Product;
import com.sprotshop.sportstore.entity.ProductSize;
import com.sprotshop.sportstore.request.ProductCardDto;
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.Hibernate;

import java.math.BigDecimal;
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
    private BigDecimal price;
    private BigDecimal costPrice;
    private Integer stockQuantity; // Total stock quantity
    private List<ImageInfo> images;
    private Long categoryId;
    private String categoryName;
    private String categoryDescription;
    private Long brandId;
    private String brandName;
    private String brandDescription;
    private String brandLogoUrl;
    private List<ProductSizeInfo> sizes; // Added sizes info
    private BigDecimal averageRating ;
    private Integer reviewCount ;
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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductSizeInfo { // New inner class for size response
        private Long id;
        private String size;
        private Integer stockQuantity;
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

        // 👈 Added: Brand info
        Long brandId = null; String brandName = null; String brandDesc = null; String brandLogo = null;
        if (product.getBrand() != null && Hibernate.isInitialized(product.getBrand())) {
            brandId = product.getBrand().getId();
            brandName = product.getBrand().getName();
            brandDesc = product.getBrand().getDescription();
            brandLogo = product.getBrand().getLogoUrl();
        }

        List<ProductSizeInfo> sizeInfos = Collections.emptyList();
        if (product.getProductSizes() != null && Hibernate.isInitialized(product.getProductSizes())) {
            if (!product.getProductSizes().isEmpty()) {
                sizeInfos = product.getProductSizes().stream()
                        .map(ps -> ProductSizeInfo.builder()
                                .id(ps.getId())
                                .size(ps.getSize())
                                .stockQuantity(ps.getStockQuantity())
                                .build())
                        .collect(Collectors.toList());
            }
        }

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .costPrice(product.getCostPrice())
                .stockQuantity(product.getStockQuantity()) // This is the total stock
                .images(imageInfos)
                .categoryId(catId)
                .categoryName(catName)
                .categoryDescription(catDesc)
                .brandId(brandId)
                .brandName(brandName)
                .brandDescription(brandDesc)
                .averageRating(product.getAverageRating())
                .reviewCount(product.getReviewCount())
                .brandLogoUrl(brandLogo)
                .sizes(sizeInfos) // Add sizes to response
                .build();
    }

    public static List<ProductResponse> fromEntities(List<Product> products) {
        if (products == null || products.isEmpty()) return Collections.emptyList();
        return products.stream().map(ProductResponse::fromEntity).collect(Collectors.toList());
    }
    // ProductResponse.java - thêm vào cuối class
    public static ProductCardDto toCardDto(Product product) {
        if (product == null) return null;

        String firstImage = product.getImages() != null && !product.getImages().isEmpty()
                ? product.getImages().iterator().next().getImageUrl()
                : "/images/default-product.jpg";

        String brandName = product.getBrand() != null ? product.getBrand().getName() : null;

        Double avgRating = product.getAverageRating() != null
                ? product.getAverageRating().doubleValue()
                : 0.0;

        Integer reviewCnt = product.getReviewCount() != null ? product.getReviewCount() : 0;

        return new ProductCardDto(
                product.getId(),
                product.getName(),
                firstImage,
                product.getPrice(),
                Math.round(avgRating * 10.0) / 10.0,
                reviewCnt,
                brandName,
                null
        );
    }

    public static List<ProductCardDto> toCardDtos(List<Product> products) {
        if (products == null || products.isEmpty()) return List.of();
        return products.stream()
                .map(ProductResponse::toCardDto)
                .toList();
    }
}