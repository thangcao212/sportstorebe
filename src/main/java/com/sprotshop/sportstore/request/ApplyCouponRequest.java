// ApplyCouponRequest.java - Updated for order integration
package com.sprotshop.sportstore.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyCouponRequest {
    @NotBlank(message = "Mã coupon không được để trống")
    private String code;

    @NotNull(message = "Tổng đơn hàng không được để trống")
    private BigDecimal orderTotal;
}