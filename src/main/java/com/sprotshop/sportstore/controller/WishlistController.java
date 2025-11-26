// Updated WishlistController.java - Remove userId from paths, use current user
package com.sprotshop.sportstore.controller;

import com.sprotshop.sportstore.request.WishlistItemRequest;
import com.sprotshop.sportstore.response.ApiResponse;
import com.sprotshop.sportstore.response.WishlistResponse;
import com.sprotshop.sportstore.service.WishlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wishlists")
@RequiredArgsConstructor
public class WishlistController {
    private final WishlistService wishlistService;


    @PreAuthorize("isAuthenticated()")
    @GetMapping("/current")
    public ResponseEntity<ApiResponse<WishlistResponse>> getCurrentWishlist() {
        return ResponseEntity.ok(ApiResponse.success("Danh sách yêu thích của bạn", wishlistService.getWishlistByCurrentUser()));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/add")
    public ResponseEntity<ApiResponse<WishlistResponse>> addToWishlist(@Valid @RequestBody WishlistItemRequest request) {
        WishlistResponse response = wishlistService.addToWishlist(request);
        return ResponseEntity.ok(ApiResponse.success("Thêm vào yêu thích thành công", response));
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/remove/{productId}")
    public ResponseEntity<ApiResponse<WishlistResponse>> removeFromWishlist(@PathVariable Long productId) {
        WishlistResponse response = wishlistService.removeFromWishlist(productId);
        return ResponseEntity.ok(ApiResponse.success("Xóa khỏi yêu thích thành công", response));
    }
}