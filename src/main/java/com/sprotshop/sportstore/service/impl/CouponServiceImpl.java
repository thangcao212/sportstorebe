// CouponServiceImpl.java - Updated without CouponUsage: Use OrderRepository to count per user usage
package com.sprotshop.sportstore.service.impl;

import com.sprotshop.sportstore.Enum.CouponType;
import com.sprotshop.sportstore.Enum.OrderStatus;
import com.sprotshop.sportstore.entity.Coupon;
import com.sprotshop.sportstore.entity.Order;
import com.sprotshop.sportstore.entity.User;
import com.sprotshop.sportstore.exception.NotFoundException;
import com.sprotshop.sportstore.exception.InvalidCouponException;
import com.sprotshop.sportstore.repository.CouponRepository;
import com.sprotshop.sportstore.repository.OrderRepository;
import com.sprotshop.sportstore.request.ApplyCouponRequest;
import com.sprotshop.sportstore.request.CouponRequest;
import com.sprotshop.sportstore.response.CouponResponse;
import com.sprotshop.sportstore.response.PageResponse;
import com.sprotshop.sportstore.service.CouponService;
import com.sprotshop.sportstore.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final CouponRepository couponRepository;
    private final OrderRepository orderRepository;  // NEW: To count per user usage
    private final UserService userService;

    @Override
    @Transactional
    @CacheEvict(value = "allCoupons", allEntries = true)
    public CouponResponse createCoupon(CouponRequest request) {
        try {
            log.info("Creating coupon: {}", request.getCode());
            // Validate endDate > startDate
            if (request.getEndDate().isBefore(request.getStartDate())) {
                throw new InvalidCouponException("Ngày kết thúc phải sau ngày bắt đầu");
            }

            Coupon coupon = Coupon.builder()
                    .code(request.getCode().toUpperCase())  // Normalize
                    .description(request.getDescription())
                    .discountAmount(request.getDiscountAmount())
                    .discountPercentage(request.getDiscountPercentage())
                    .minOrderValue(request.getMinOrderValue())
                    .maxUsagePerUser(request.getMaxUsagePerUser())
                    .totalUsageLimit(request.getTotalUsageLimit())
                    .startDate(request.getStartDate())
                    .maxUsagePerUser(request.getMaxUsagePerUser())
                    .maxApplicableOrderValue(request.getMaxApplicableOrderValue())
                    .endDate(request.getEndDate())
                    .type(request.getType())
                    .usedCount(0)
                    .build();

            Coupon savedCoupon = couponRepository.save(coupon);
            log.info("Coupon created successfully: ID={}", savedCoupon.getId());
            return CouponResponse.fromEntity(savedCoupon);
        } catch (Exception e) {
            log.error("Create coupon failed for code {}: {}", request.getCode(), e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional
    public CouponResponse updateCoupon(Long couponId, CouponRequest request) {
        try {
            log.info("Updating couponId: {}", couponId);
            Coupon coupon = couponRepository.findById(couponId)
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy coupon: " + couponId));

            // Validate endDate > startDate
            if (request.getEndDate().isBefore(request.getStartDate())) {
                throw new InvalidCouponException("Ngày kết thúc phải sau ngày bắt đầu");
            }

            coupon.setCode(request.getCode().toUpperCase());
            coupon.setDescription(request.getDescription());
            coupon.setDiscountAmount(request.getDiscountAmount());
            coupon.setDiscountPercentage(request.getDiscountPercentage());
            coupon.setMinOrderValue(request.getMinOrderValue());
            coupon.setMaxUsagePerUser(request.getMaxUsagePerUser());
            coupon.setTotalUsageLimit(request.getTotalUsageLimit());
            coupon.setStartDate(request.getStartDate());
            coupon.setMaxDiscountAmount(request.getMaxDiscountAmount());
            coupon.setMaxApplicableOrderValue(request.getMaxApplicableOrderValue());
            coupon.setEndDate(request.getEndDate());
            coupon.setType(request.getType());

            Coupon updatedCoupon = couponRepository.save(coupon);
            log.info("Coupon updated successfully: ID={}", couponId);
            return CouponResponse.fromEntity(updatedCoupon);
        } catch (Exception e) {
            log.error("Update coupon failed for ID {}: {}", couponId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional
    public void deleteCoupon(Long couponId) {
        try {
            log.info("Deleting couponId: {}", couponId);
            Coupon coupon = couponRepository.findById(couponId)
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy coupon: " + couponId));
            if (coupon.getUsedCount() > 0 || !coupon.getOrders().isEmpty()) {
                throw new InvalidCouponException("Không thể xóa coupon đã được sử dụng");
            }
            couponRepository.delete(coupon);
            log.info("Coupon deleted successfully: ID={}", couponId);
        } catch (Exception e) {
            log.error("Delete coupon failed for ID {}: {}", couponId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CouponResponse> getAllCoupons(Pageable pageable) {
        log.info("Fetching all coupons");
        Page<Coupon> coupons = couponRepository.findAll(pageable);
        return PageResponse.fromPage(coupons.map(CouponResponse::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    public CouponResponse getCouponById(Long couponId) {
        log.info("Fetching couponId: {}", couponId);
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy coupon: " + couponId));
        return CouponResponse.fromEntity(coupon);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CouponResponse> getValidCoupons(BigDecimal minOrderValue) {
        log.info("Fetching valid coupons for minOrderValue: {}", minOrderValue);
        LocalDateTime now = LocalDateTime.now();
        List<Coupon> coupons = couponRepository.findValidCoupons(now, minOrderValue);
        return coupons.stream().map(CouponResponse::fromEntity).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal applyCoupon(ApplyCouponRequest request) {
        try {
            User currentUser = userService.getCurrentLoggedInUser();
            Long userId = currentUser.getId();
            String couponCode = request.getCode().trim().toUpperCase();

            log.info("Applying coupon {} for userId: {}, orderTotal: {}",
                    couponCode, userId, request.getOrderTotal());

            LocalDateTime now = LocalDateTime.now();

            // 1. Tìm coupon hợp lệ (đang trong thời gian + còn lượt)
            Coupon coupon = couponRepository.findValidByCode(couponCode, now)
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy coupon hợp lệ: " + couponCode));

            // 2. Kiểm tra giá trị đơn hàng tối thiểu
            if (request.getOrderTotal().compareTo(coupon.getMinOrderValue()) < 0) {
                throw new InvalidCouponException(
                        String.format("Đơn hàng phải từ %,dđ để sử dụng coupon này", coupon.getMinOrderValue().longValue()));
            }

            // 3. Kiểm tra tổng lượt sử dụng (nếu có giới hạn)
            if (coupon.getTotalUsageLimit() != null
                    && coupon.getUsedCount() >= coupon.getTotalUsageLimit()) {
                throw new InvalidCouponException("Coupon đã hết lượt sử dụng toàn hệ thống");
            }

            // 4. Kiểm tra lượt dùng của user này
            long userUsedCount = orderRepository.countByUserIdAndCouponIdAndStatus(
                    userId, coupon.getId(), OrderStatus.COMPLETED);

            if (coupon.getMaxUsagePerUser() != null
                    && userUsedCount >= coupon.getMaxUsagePerUser()) {
                throw new InvalidCouponException("Bạn đã sử dụng hết lượt coupon này");
            }

            // 5. TÍNH GIẢM GIÁ - PHIÊN BẢN HOÀN CHỈNH CÓ CAP
            BigDecimal discount;

            if (coupon.getType() == CouponType.FIXED) {
                discount = coupon.getDiscountAmount();

            } else { // PERCENTAGE
                // Tính % trước
                discount = request.getOrderTotal()
                        .multiply(coupon.getDiscountPercentage())
                        .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);

                // ÁP DỤNG CAP TỐI ĐA (nếu có)
                if (coupon.getMaxDiscountAmount() != null
                        && discount.compareTo(coupon.getMaxDiscountAmount()) > 0) {

                    log.info("Coupon {}: Giảm {}% = {}đ → bị giới hạn CAP còn {}đ",
                            coupon.getCode(),
                            coupon.getDiscountPercentage(),
                            discount,
                            coupon.getMaxDiscountAmount());

                    discount = coupon.getMaxDiscountAmount();
                }

                // (TÙY CHỌN) Giới hạn đơn hàng tối đa được áp %
                if (coupon.getMaxApplicableOrderValue() != null
                        && request.getOrderTotal().compareTo(coupon.getMaxApplicableOrderValue()) > 0) {

                    throw new InvalidCouponException(
                            String.format("Coupon chỉ áp dụng cho đơn hàng tối đa %,dđ",
                                    coupon.getMaxApplicableOrderValue().longValue()));
                }
            }

            // Làm tròn 2 chữ số cuối
            discount = discount.setScale(2, RoundingMode.HALF_UP);

            log.info("Coupon {} áp dụng thành công → Giảm: {}đ ({}%)",
                    coupon.getCode(), discount,
                    coupon.getType() == CouponType.PERCENTAGE
                            ? coupon.getDiscountPercentage() : "FIXED");

            return discount;

        } catch (Exception e) {
            log.error("Apply coupon failed for code {}: {}", request.getCode(), e.getMessage(), e);
            throw e;
        }
    }
}