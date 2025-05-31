package com.sprotshop.sportstore.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartResponse {
    private Long cartId;
    private Long userId; // ID của người dùng sở hữu giỏ hàng
    private List<CartItemResponse> items; // Danh sách các món hàng trong giỏ
    private Integer totalItems; // Tổng số lượng các món hàng
    private Double totalPrice;
    // Tổng tiền của giỏ hàng
}