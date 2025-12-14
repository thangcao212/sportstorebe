// CouponResponse.java - Updated with scope information
package com.sprotshop.sportstore.response;

import com.sprotshop.sportstore.entity.Coupon;
import com.sprotshop.sportstore.Enum.CouponScope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
    private String scope;
    private BigDecimal maxDiscountAmount;
    private BigDecimal maxApplicableOrderValue;
    private List<Long> productIds;
    private Long categoryId;
    private Long brandId;

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
                .scope(coupon.getScope().name())
                .maxDiscountAmount(coupon.getMaxDiscountAmount())
                .maxApplicableOrderValue(coupon.getMaxApplicableOrderValue())
                .productIds(coupon.getApplicableProducts() != null ?
                        coupon.getApplicableProducts().stream()
                                .map(product -> product.getId())
                                .toList() : null)
                .categoryId(coupon.getApplicableCategory() != null ?
                        coupon.getApplicableCategory().getId() : null)
                .brandId(coupon.getApplicableBrand() != null ?
                        coupon.getApplicableBrand().getId() : null)
                .build();
    }
}