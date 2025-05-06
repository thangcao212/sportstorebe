package com.sprotshop.sportstore.response; // Thay đổi package nếu cần

import com.sprotshop.sportstore.entity.Image;
import com.sprotshop.sportstore.entity.OrderItem;
import com.sprotshop.sportstore.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.util.CollectionUtils;


import java.math.BigDecimal;

/**
 * DTO chứa thông tin chi tiết của một mục trong đơn hàng trả về cho client.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {
    private Long orderItemId;
    private Long productId;
    private String productName;
    private String productImageUrl;
    private Integer quantity;
    private BigDecimal price; // Giá tại thời điểm đặt hàng

    public static OrderItemResponse fromEntity(OrderItem item) {
        if (item == null || item.getProduct() == null) return null;

        Product product = item.getProduct();
        String imageUrl = null;
        if (!CollectionUtils.isEmpty(product.getImages()) && Hibernate.isInitialized(product.getImages())) {
            imageUrl = product.getImages().stream().findFirst().map(Image::getImageUrl).orElse(null);
        }

        return OrderItemResponse.builder()
                .orderItemId(item.getId())
                .productId(product.getId())
                .productName(product.getName())
                .productImageUrl(imageUrl)
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .build();
    }
}