// CouponService.java - Interface
package com.sprotshop.sportstore.service;

import com.sprotshop.sportstore.request.ApplyCouponRequest;
import com.sprotshop.sportstore.request.CouponRequest;
import com.sprotshop.sportstore.response.CouponResponse;
import com.sprotshop.sportstore.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

public interface CouponService {
    CouponResponse createCoupon(CouponRequest request);
    CouponResponse updateCoupon(Long couponId, CouponRequest request);
    void deleteCoupon(Long couponId);
    PageResponse<CouponResponse> getAllCoupons(Pageable pageable);
    CouponResponse getCouponById(Long couponId);
    List<CouponResponse> getValidCoupons(BigDecimal minOrderValue);
    BigDecimal applyCoupon(ApplyCouponRequest request);
}