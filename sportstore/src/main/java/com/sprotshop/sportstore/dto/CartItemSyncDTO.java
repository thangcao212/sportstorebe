package com.sprotshop.sportstore.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for receiving cart item data from client for synchronization
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemSyncDTO {

    private Long productId;
    private Integer quantity;
}