package com.sprotshop.sportstore.controller;

import com.sprotshop.sportstore.entity.User; // Import User entity
import com.sprotshop.sportstore.request.AddToCartRequest;
import com.sprotshop.sportstore.request.UpdateCartItemRequest;
import com.sprotshop.sportstore.response.ApiResponse;
import com.sprotshop.sportstore.response.CartResponse;
import com.sprotshop.sportstore.service.CartService;
import com.sprotshop.sportstore.service.UserService; // Import UserService
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private static final Logger log = LoggerFactory.getLogger(CartController.class);
    private final CartService cartService;
    private final UserService userService; // Inject UserService để lấy user hiện tại

    @GetMapping
    @PreAuthorize("isAuthenticated()") // Yêu cầu đăng nhập
    public ResponseEntity<ApiResponse<CartResponse>> getCurrentUserCart() {
        // Đã bỏ việc lấy userId ở đây, Service sẽ tự làm
        log.info("GET /api/cart - Lấy giỏ hàng cho user đang đăng nhập");
        CartResponse cartResponse = cartService.getCart();
        ApiResponse<CartResponse> response = ApiResponse.<CartResponse>builder()
                .message("Success")
                .data(cartResponse)
                .status(HttpStatus.OK.value())
                .build();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/items")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CartResponse>> addItemToCurrentUserCart(
            @Valid @RequestBody AddToCartRequest request) {
        log.info("POST /api/cart/items - Thêm vào giỏ hàng cho user đang đăng nhập, request: {}", request);
        CartResponse cartResponse = cartService.addItemToCart(request);
        ApiResponse<CartResponse> response = ApiResponse.<CartResponse>builder()
                .message("Item added/updated in cart successfully")
                .data(cartResponse)
                .status(HttpStatus.OK.value())
                .build();
        return ResponseEntity.ok(response);
    }

    @PutMapping("/items/{cartItemId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CartResponse>> updateCartItemQuantity(
            @PathVariable Long cartItemId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        log.info("PUT /api/cart/items/{} - Cập nhật số lượng cho user đang đăng nhập, quantity: {}", cartItemId, request.getQuantity());
        CartResponse cartResponse = cartService.updateCartItemQuantity(cartItemId, request);
        ApiResponse<CartResponse> response = ApiResponse.<CartResponse>builder()
                .message("Cart item quantity updated successfully")
                .data(cartResponse)
                .status(HttpStatus.OK.value())
                .build();
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/items/product/{productId}") // << Sửa đường dẫn
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CartResponse>> removeItemFromCartByProductId(
            @PathVariable Long productId) { // << Sửa tên PathVariable
        // Lấy userId từ context bảo mật thông qua userService như các hàm khác
        // User currentUser = userService.getCurrentLoggedInUser(); // Không cần thiết vì service tự lấy
        log.warn("DELETE /api/cart/items/product/{} - Xóa sản phẩm khỏi giỏ của user đang đăng nhập", productId);

        // Gọi hàm service mới đã sửa
        CartResponse cartResponse = cartService.removeItemFromCartByProductId(productId);

        ApiResponse<CartResponse> response = ApiResponse.<CartResponse>builder()
                .message("Item removed from cart successfully based on product ID") // Sửa message nếu muốn
                .data(cartResponse) // Trả về giỏ hàng sau khi xóa
                .status(HttpStatus.OK.value())
                .build();
        return ResponseEntity.ok(response);
    }

    @DeleteMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CartResponse>> clearCurrentUserCart() {
        log.warn("DELETE /api/cart - Xóa toàn bộ giỏ hàng của user đang đăng nhập");
        CartResponse cartResponse = cartService.clearCart();
        ApiResponse<CartResponse> response = ApiResponse.<CartResponse>builder()
                .message("Cart cleared successfully")
                .data(cartResponse) // Trả về giỏ hàng rỗng
                .status(HttpStatus.OK.value())
                .build();
        return ResponseEntity.ok(response);
    }

    // Bỏ hàm helper getCurrentUserId() vì Service sẽ tự lấy
}