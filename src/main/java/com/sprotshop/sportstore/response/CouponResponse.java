// CouponResponse.java - Updated with full fields
package com.sprotshop.sportstore.response;

import com.sprotshop.sportstore.entity.Coupon;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponResponse {
    private Long id;
    private String code;
    private String description;
    private BigDecimal discountAmount;
    private BigDecimal discountPercentage;
    private BigDecimal minOrderValue;
    private Integer maxUsagePerUser;
    private Integer totalUsageLimit;
    private Integer usedCount;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String type;

    public static CouponResponse fromEntity(Coupon coupon) {
        return CouponResponse.builder()
                .id(coupon.getId())
                .code(coupon.getCode())
                .description(coupon.getDescription())
                .discountAmount(coupon.getDiscountAmount())
                .discountPercentage(coupon.getDiscountPercentage())
                .minOrderValue(coupon.getMinOrderValue())
                .maxUsagePerUser(coupon.getMaxUsagePerUser())
                .totalUsageLimit(coupon.getTotalUsageLimit())
                .usedCount(coupon.getUsedCount())
                .startDate(coupon.getStartDate())
                .endDate(coupon.getEndDate())
                .type(coupon.getType().name())
                .build();
    }
}