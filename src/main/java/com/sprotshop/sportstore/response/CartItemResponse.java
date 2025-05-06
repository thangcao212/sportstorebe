package com.sprotshop.sportstore.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemResponse {
    private Long cartItemId; // ID của CartItem
    private Long productId;
    private String productName;
    private String productImageUrl; // Lấy ảnh đầu tiên làm đại diện (ví dụ)
    private Integer quantity;
    private Double price; // Giá đơn vị hiện tại của sản phẩm
    private Double itemTotalPrice; // Thành tiền (quantity * price)
}