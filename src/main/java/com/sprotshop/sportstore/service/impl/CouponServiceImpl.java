
package com.sprotshop.sportstore.service.impl;

import com.sprotshop.sportstore.Enum.CouponType;
import com.sprotshop.sportstore.Enum.OrderStatus;
import com.sprotshop.sportstore.entity.*;
import com.sprotshop.sportstore.exception.NotFoundException;
import com.sprotshop.sportstore.exception.InvalidCouponException;
import com.sprotshop.sportstore.repository.*;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final CouponRepository couponRepository;
    private final OrderRepository orderRepository;
    private final UserService userService;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final BrandRepository brandRepository;

    @Override
    @Transactional
    @CacheEvict(value = "allCoupons", allEntries = true)
    public CouponResponse createCoupon(CouponRequest request) {
        try {
            log.info("Creating coupon: {}", request.getCode());

            // Validate dates
            if (request.getEndDate().isBefore(request.getStartDate())) {
                throw new InvalidCouponException("Ngày kết thúc phải sau ngày bắt đầu");
            }

            // Build base coupon
            Coupon coupon = Coupon.builder()
                    .code(request.getCode().toUpperCase())
                    .description(request.getDescription())
                    .discountAmount(request.getDiscountAmount())
                    .discountPercentage(request.getDiscountPercentage())
                    .minOrderValue(request.getMinOrderValue())
                    .maxUsagePerUser(request.getMaxUsagePerUser())
                    .totalUsageLimit(request.getTotalUsageLimit())
                    .startDate(request.getStartDate())
                    .maxDiscountAmount(request.getMaxDiscountAmount())
                    .maxApplicableOrderValue(request.getMaxApplicableOrderValue())
                    .endDate(request.getEndDate())
                    .type(request.getType())
                    .scope(request.getScope()) // NEW
                    .usedCount(0)
                    .build();

            // Handle scope-specific data
            handleCouponScope(coupon, request);

            Coupon savedCoupon = couponRepository.save(coupon);
            log.info("Coupon created successfully: ID={}", savedCoupon.getId());
            return CouponResponse.fromEntity(savedCoupon);

        } catch (Exception e) {
            log.error("Create coupon failed for code {}: {}", request.getCode(), e.getMessage(), e);
            throw e;
        }
    }

    private void handleCouponScope(Coupon coupon, CouponRequest request) {
        switch (request.getScope()) {
            case SPECIFIC_PRODUCTS:
                if (request.getProductIds() != null && !request.getProductIds().isEmpty()) {
                    List<Product> products = productRepository.findAllById(request.getProductIds());
                    if (products.size() != request.getProductIds().size()) {
                        throw new NotFoundException("Một số sản phẩm không tồn tại");
                    }
                    coupon.setApplicableProducts(new HashSet<>(products));
                }
                break;

            case CATEGORY:
                if (request.getCategoryId() != null) {
                    ProductCategory category = categoryRepository.findById(request.getCategoryId())
                            .orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục: " + request.getCategoryId()));
                    coupon.setApplicableCategory(category);
                }
                break;

            case BRAND:
                if (request.getBrandId() != null) {
                    Brand brand = brandRepository.findById(request.getBrandId())
                            .orElseThrow(() -> new NotFoundException("Không tìm thấy thương hiệu: " + request.getBrandId()));
                    coupon.setApplicableBrand(brand);
                }
                break;

            case ALL_PRODUCTS:
            default:

                break;
        }
    }

    @Override
    @Transactional
    public CouponResponse updateCoupon(Long couponId, CouponRequest request) {
        try {
            log.info("Updating couponId: {}", couponId);

            Coupon coupon = couponRepository.findById(couponId)
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy coupon: " + couponId));

            // Validate dates
            if (request.getEndDate().isBefore(request.getStartDate())) {
                throw new InvalidCouponException("Ngày kết thúc phải sau ngày bắt đầu");
            }

            // Update basic fields
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
            coupon.setScope(request.getScope());

            // Clear old scope data
            coupon.setApplicableProducts(new HashSet<>());
            coupon.setApplicableCategory(null);
            coupon.setApplicableBrand(null);

            // Handle new scope data
            handleCouponScope(coupon, request);

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

            LocalDateTime now = LocalDateTime.now();

            Coupon coupon = couponRepository.findValidByCode(couponCode, now)
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy coupon hợp lệ: " + couponCode));

            // 1. Min order value
            if (request.getOrderTotal().compareTo(coupon.getMinOrderValue()) < 0) {
                throw new InvalidCouponException(
                        String.format("Đơn hàng phải từ %,dđ để sử dụng coupon này", coupon.getMinOrderValue().longValue()));
            }

            // 2. Total usage limit
            if (coupon.getTotalUsageLimit() != null && coupon.getUsedCount() >= coupon.getTotalUsageLimit()) {
                throw new InvalidCouponException("Coupon đã hết lượt sử dụng toàn hệ thống");
            }

            // 3. Per user limit
            long userUsedCount = orderRepository.countByUserIdAndCouponIdAndStatus(
                    userId, coupon.getId(), OrderStatus.COMPLETED);
            if (coupon.getMaxUsagePerUser() != null && userUsedCount >= coupon.getMaxUsagePerUser()) {
                throw new InvalidCouponException("Bạn đã sử dụng hết lượt coupon này");
            }

            // 4. KIỂM TRA SCOPE - QUAN TRỌNG NHẤT
            if (!request.getCartItems().isEmpty()) {
                boolean applicable = isCouponApplicableToCart(coupon, request.getCartItems());
                if (!applicable) {
                    throw new InvalidCouponException("Coupon không áp dụng cho sản phẩm trong giỏ hàng");
                }
            }

            // 5. Max applicable order value
            if (coupon.getMaxApplicableOrderValue() != null
                    && request.getOrderTotal().compareTo(coupon.getMaxApplicableOrderValue()) > 0) {
                throw new InvalidCouponException(
                        String.format("Coupon chỉ áp dụng cho đơn hàng tối đa %,dđ",
                                coupon.getMaxApplicableOrderValue().longValue()));
            }

            // 6. Tính discount (có cap)
            BigDecimal discount;
            if (coupon.getType() == CouponType.FIXED) {
                discount = coupon.getDiscountAmount();
            } else {
                discount = request.getOrderTotal()
                        .multiply(coupon.getDiscountPercentage())
                        .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);

                if (coupon.getMaxDiscountAmount() != null
                        && discount.compareTo(coupon.getMaxDiscountAmount()) > 0) {
                    discount = coupon.getMaxDiscountAmount();
                }
            }

            discount = discount.setScale(2, RoundingMode.HALF_UP);

            log.info("Coupon {} validate thành công → Giảm: {}đ", coupon.getCode(), discount);
            return discount;

        } catch (Exception e) {
            log.error("Apply coupon failed: {}", e.getMessage());
            throw e;
        }
    }

    // Helper method mới - kiểm tra scope
    private boolean isCouponApplicableToCart(Coupon coupon, Set<CartItem> cartItems) {
        return switch (coupon.getScope()) {
            case ALL_PRODUCTS -> true;

            case SPECIFIC_PRODUCTS -> cartItems.stream()
                    .anyMatch(item -> coupon.getApplicableProducts()
                            .contains(item.getProduct()));

            case CATEGORY -> {
                if (coupon.getApplicableCategory() == null) yield false;
                yield cartItems.stream()
                        .anyMatch(item -> item.getProduct().getProductCategory()
                                .equals(coupon.getApplicableCategory()));
            }

            case BRAND -> {
                if (coupon.getApplicableBrand() == null) yield false;
                yield cartItems.stream()
                        .anyMatch(item -> item.getProduct().getBrand()
                                .equals(coupon.getApplicableBrand()));
            }

            default -> true;
        };
    }
}