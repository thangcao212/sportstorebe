// CouponController.java - Updated messages in Vietnamese for consistency
package com.sprotshop.sportstore.controller;

import com.sprotshop.sportstore.request.ApplyCouponRequest;
import com.sprotshop.sportstore.request.CouponRequest;
import com.sprotshop.sportstore.response.ApiResponse;
import com.sprotshop.sportstore.response.CouponResponse;
import com.sprotshop.sportstore.response.PageResponse;
import com.sprotshop.sportstore.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {
    private final CouponService couponService;

    @PostMapping
    public ResponseEntity<ApiResponse<CouponResponse>> createCoupon(@Valid @RequestBody CouponRequest request) {
        CouponResponse response = couponService.createCoupon(request);
        return ResponseEntity.ok(ApiResponse.success("Mã giảm giá được tạo thành công", response));
    }

    @PutMapping("/{couponId}")
    public ResponseEntity<ApiResponse<CouponResponse>> updateCoupon(@PathVariable Long couponId, @Valid @RequestBody CouponRequest request) {
        CouponResponse response = couponService.updateCoupon(couponId, request);
        return ResponseEntity.ok(ApiResponse.success("Mã giảm giá được cập nhật thành công", response));
    }

    @DeleteMapping("/{couponId}")
    public ResponseEntity<ApiResponse<Void>> deleteCoupon(@PathVariable Long couponId) {
        couponService.deleteCoupon(couponId);
        return ResponseEntity.ok(ApiResponse.success("Mã giảm giá được xóa thành công", null));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CouponResponse>>> getAllCoupons(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Danh sách mã giảm giá", couponService.getAllCoupons(pageable)));
    }

    @GetMapping("/{couponId}")
    public ResponseEntity<ApiResponse<CouponResponse>> getCouponById(@PathVariable Long couponId) {
        return ResponseEntity.ok(ApiResponse.success("Chi tiết mã giảm giá", couponService.getCouponById(couponId)));
    }

    @GetMapping("/valid")
    public ResponseEntity<ApiResponse<List<CouponResponse>>> getValidCoupons(@RequestParam(required = false) BigDecimal minOrderValue) {
        return ResponseEntity.ok(ApiResponse.success("Danh sách mã giảm giá hợp lệ", couponService.getValidCoupons(minOrderValue)));
    }

    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<BigDecimal>> applyCoupon(@Valid @RequestBody ApplyCouponRequest request) {
        BigDecimal discount = couponService.applyCoupon(request);
        return ResponseEntity.ok(ApiResponse.success("Áp dụng mã giảm giá thành công", discount));
    }
}