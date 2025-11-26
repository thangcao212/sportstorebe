// WishlistItemResponse.java - DTO for wishlist items
package com.sprotshop.sportstore.response;

import com.sprotshop.sportstore.entity.Product;
import com.sprotshop.sportstore.entity.WishlistItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WishlistItemResponse {
    private Long id;
    private Long productId;
    private String productName;
    private BigDecimal productPrice;
//    private String size;
private String productImageUrl;

    public static WishlistItemResponse fromEntity(WishlistItem item) {
        Product product = item.getProduct();
        return WishlistItemResponse.builder()
                .id(item.getId())
                .productId(product.getId())
                .productName(product.getName())
                .productPrice(product.getPrice())
                .productImageUrl(item.getProduct().getImages().stream().findFirst()
                        .map(image -> image.getImageUrl()).orElse(null))

//                .size(item.getSize())
                .build();
    }
}