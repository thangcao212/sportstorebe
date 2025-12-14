// ApplyCouponRequest.java - Updated for order integration
package com.sprotshop.sportstore.request;

import com.sprotshop.sportstore.entity.CartItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyCouponRequest {
    @NotBlank(message = "Mã coupon không được để trống")
    private String code;

    @NotNull(message = "Tổng đơn hàng không được để trống")
    private BigDecimal orderTotal;

    private Set<CartItem> cartItems;
}