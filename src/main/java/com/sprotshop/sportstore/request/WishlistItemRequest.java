// WishlistItemRequest.java - DTO for adding/removing items
package com.sprotshop.sportstore.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WishlistItemRequest {
    @NotNull(message = "Product ID không được để trống")
    private Long productId;

    private String size;  // Optional
}