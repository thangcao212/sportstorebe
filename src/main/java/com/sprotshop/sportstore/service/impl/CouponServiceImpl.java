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
    @Transactional
    public BigDecimal applyCoupon(ApplyCouponRequest request) {
        try {
            User currentUser = userService.getCurrentLoggedInUser();
            Long userId = currentUser.getId();
            log.info("Applying coupon {} for userId: {}, orderTotal: {}", request.getCode(), userId, request.getOrderTotal());

            LocalDateTime now = LocalDateTime.now();
            Coupon coupon = couponRepository.findValidByCode(request.getCode(), now)
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy coupon hợp lệ: " + request.getCode()));

            // Check minOrderValue
            if (request.getOrderTotal().compareTo(coupon.getMinOrderValue()) < 0) {
                throw new InvalidCouponException("Đơn hàng không đủ giá trị tối thiểu: " + coupon.getMinOrderValue());
            }

            // Check totalUsageLimit (if exists)
            if (coupon.getTotalUsageLimit() != null && coupon.getUsedCount() >= coupon.getTotalUsageLimit()) {
                throw new InvalidCouponException("Coupon đã hết lượt sử dụng");
            }

            // Check maxUsagePerUser: Count from user's completed orders with this coupon
            long userUsedCount = orderRepository.countByUserIdAndCouponIdAndStatus(userId, coupon.getId(), OrderStatus.COMPLETED);
            if (coupon.getMaxUsagePerUser() != null && userUsedCount >= coupon.getMaxUsagePerUser()) {
                throw new InvalidCouponException("Bạn đã sử dụng hết lượt coupon này");
            }

            // Calculate discount
            BigDecimal discount;
            if (coupon.getType() == CouponType.FIXED) {
                discount = coupon.getDiscountAmount();
            } else {
                discount = request.getOrderTotal()
                        .multiply(coupon.getDiscountPercentage()
                                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
            }

            log.info("Coupon applied successfully: discount={}", discount);
            return discount;
        } catch (Exception e) {
            log.error("Apply coupon failed for code {}: {}", request.getCode(), e.getMessage(), e);
            throw e;
        }
    }

}