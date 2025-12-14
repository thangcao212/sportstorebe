// src/main/java/com/sprotshop/sportstore/request/CouponRequest.java
package com.sprotshop.sportstore.request;

import com.sprotshop.sportstore.Enum.CouponScope;
import com.sprotshop.sportstore.Enum.CouponType;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CouponRequest {

    @NotBlank(message = "Mã không được để trống")
    private String code;

    private String description;

    @NotNull(message = "Loại giảm giá không được để trống")
    private CouponType type;

    @NotNull(message = "Phạm vi áp dụng không được để trống")
    private CouponScope scope;

    // Danh sách ID sản phẩm áp dụng (nếu scope = SPECIFIC_PRODUCTS)
    private List<Long> productIds;

    // ID danh mục (nếu scope = CATEGORY)
    private Long categoryId;

    // ID thương hiệu (nếu scope = BRAND)
    private Long brandId;

    private BigDecimal discountAmount;
    private BigDecimal discountPercentage;

    // ← BẮT BUỘC với coupon %
    private BigDecimal maxDiscountAmount;

    // ← Tùy chọn: giới hạn đơn tối đa được áp %
    private BigDecimal maxApplicableOrderValue;

    @DecimalMin(value = "0.0", message = "Giá trị tối thiểu phải >= 0")
    private BigDecimal minOrderValue;

    @Min(value = 1, message = "Số lần / người phải >= 1")
    private Integer maxUsagePerUser;

    @Min(value = 1, message = "Tổng lượt sử dụng phải >= 1")
    private Integer totalUsageLimit;

    @NotNull(message = "Ngày bắt đầu không được để trống")
    private LocalDateTime startDate;

    @NotNull(message = "Ngày kết thúc không được để trống")
    private LocalDateTime endDate;

    @AssertTrue(message = "Coupon áp dụng sản phẩm cụ thể phải có danh sách sản phẩm")
    private boolean isSpecificProductsValid() {
        if (scope == CouponScope.SPECIFIC_PRODUCTS) {
            return productIds != null && !productIds.isEmpty();
        }
        return true;
    }

    @AssertTrue(message = "Coupon áp dụng danh mục phải có categoryId")
    private boolean isCategoryValid() {
        if (scope == CouponScope.CATEGORY) {
            return categoryId != null;
        }
        return true;
    }

    @AssertTrue(message = "Coupon áp dụng thương hiệu phải có brandId")
    private boolean isBrandValid() {
        if (scope == CouponScope.BRAND) {
            return brandId != null;
        }
        return true;
    }


    // VALIDATION: Coupon % phải có % và CAP
    @AssertTrue(message = "Coupon giảm % phải có phần trăm giảm và giới hạn tiền giảm tối đa")
    private boolean isPercentageValid() {
        if (type == CouponType.PERCENTAGE) {
            return discountPercentage != null
                    && discountPercentage.compareTo(BigDecimal.ZERO) > 0
                    && discountPercentage.compareTo(BigDecimal.valueOf(100)) < 0
                    && discountAmount == null
                    && maxDiscountAmount != null
                    && maxDiscountAmount.compareTo(BigDecimal.ZERO) > 0;
        }
        return true;
    }

    // VALIDATION: Coupon FIXED chỉ được có tiền cố định
    @AssertTrue(message = "Coupon cố định chỉ được nhập số tiền giảm")
    private boolean isFixedValid() {
        if (type == CouponType.FIXED) {
            return discountAmount != null
                    && discountAmount.compareTo(BigDecimal.ZERO) > 0
                    && discountPercentage == null;
        }
        return true;
    }

    @AssertTrue(message = "Ngày kết thúc phải sau ngày bắt đầu")
    private boolean isDateValid() {
        return endDate != null && startDate != null && endDate.isAfter(startDate);
    }
}