// CouponRequest.java (corrected validation)
package com.sprotshop.sportstore.request;

import com.sprotshop.sportstore.Enum.CouponType;  // Giả sử enum FIXED/PERCENTAGE
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CouponRequest {
    @NotBlank(message = "Mã không được để trống")
    private String code;

    private String description;

    @NotNull(message = "Loại giảm giá không được để trống")
    private CouponType type;

    @DecimalMin(value = "0.0", inclusive = false, message = "Số tiền giảm phải > 0")
    private BigDecimal discountAmount;  // Bỏ @NotNull ở đây

    @DecimalMin(value = "0.0", inclusive = false, message = "Phần trăm giảm phải > 0")
    @DecimalMax(value = "100.0", inclusive = false, message = "Phần trăm giảm phải < 100")
    private BigDecimal discountPercentage;  // Bỏ @NotNull ở đây

    @DecimalMin(value = "0.0", message = "Giá trị tối thiểu phải >= 0")
    private BigDecimal minOrderValue;

    @Min(value = 1, message = "Số lần / người phải >= 1")
    private Integer maxUsagePerUser;

    @Min(value = 1, message = "Tổng lượt sử dụng phải >= 1")
    private Integer totalUsageLimit;

    @NotNull(message = "Ngày bắt đầu không được để trống")
    private LocalDateTime startDate;

    @NotNull(message = "Ngày kết thúc không được để trống")
    // 👈 Fix: Bỏ @AssertFalse (sai vì AssertFalse chỉ cho boolean, không phải LocalDateTime)
    private LocalDateTime endDate;

    // 👈 Giữ nguyên: Validator conditional cho discount
    @AssertTrue(message = "Phải cung cấp đúng field giảm giá theo loại")
    public boolean isDiscountValid() {
        if (type == null) return false;
        if (type == CouponType.FIXED) {
            return discountAmount != null && discountPercentage == null;  // Chỉ require FIXED
        } else if (type == CouponType.PERCENTAGE) {
            return discountPercentage != null && discountAmount == null;  // Chỉ require PERCENTAGE
        }
        return false;
    }

    // 👈 Giữ nguyên: Check endDate > startDate qua method @AssertTrue
    @AssertTrue(message = "Ngày kết thúc phải sau ngày bắt đầu")
    public boolean isDateValid() {
        return endDate == null || startDate == null || endDate.isAfter(startDate);
    }
}