// WishlistResponse.java - DTO for returning wishlist (with items)
package com.sprotshop.sportstore.response;

import com.sprotshop.sportstore.entity.Wishlist;
import com.sprotshop.sportstore.entity.WishlistItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WishlistResponse {
    private Long id;
    private UserDTO user;
    private List<WishlistItemResponse> items;  // Nested items
    private LocalDateTime createdAt;

    // Factory method from entity
    public static WishlistResponse fromEntity(Wishlist wishlist) {
        return WishlistResponse.builder()
                .id(wishlist.getId())
                .user(UserDTO.builder()
                        .userId(wishlist.getUser().getId())
                        .email(wishlist.getUser().getEmail())
                        .build())
                .items(wishlist.getWishlistItems().stream().map(WishlistItemResponse::fromEntity).collect(Collectors.toList()))
                .createdAt(wishlist.getCreatedAt())
                .build();
    }
}