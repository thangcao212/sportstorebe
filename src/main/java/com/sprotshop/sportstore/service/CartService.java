package com.sprotshop.sportstore.service;

import com.sprotshop.sportstore.request.AddToCartRequest;
import com.sprotshop.sportstore.request.UpdateCartItemRequest;
import com.sprotshop.sportstore.response.CartResponse;

public interface CartService {

    /** Lấy giỏ hàng của user đang đăng nhập (tạo nếu chưa có). */
    CartResponse getCart();

    /** Thêm/Cập nhật item cho user đang đăng nhập. */
    CartResponse addItemToCart(AddToCartRequest request);

    /** Cập nhật số lượng item cho user đang đăng nhập. */
    CartResponse updateCartItemQuantity(Long cartItemId, UpdateCartItemRequest request);

    /** Xóa item khỏi giỏ của user đang đăng nhập. */
    CartResponse removeItemFromCartByProductId(Long productId);

    /** Xóa sạch giỏ hàng của user đang đăng nhập. */
    CartResponse clearCart();
}