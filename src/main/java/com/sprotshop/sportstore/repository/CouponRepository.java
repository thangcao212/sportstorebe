// CouponRepository.java - Full repository with custom queries
package com.sprotshop.sportstore.repository;

import com.sprotshop.sportstore.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByCode(String code);

    @Query("SELECT c FROM Coupon c WHERE c.startDate <= :now AND c.endDate >= :now " +
            "AND (c.minOrderValue IS NULL OR c.minOrderValue <= :minOrderValue) " +
            "AND (c.totalUsageLimit IS NULL OR c.usedCount < c.totalUsageLimit)")
    List<Coupon> findValidCoupons(@Param("now") LocalDateTime now, @Param("minOrderValue") BigDecimal minOrderValue);

    // NEW: Find by code and check if usable by user (if maxUsagePerUser, need separate logic in service)
    @Query("SELECT c FROM Coupon c WHERE c.code = :code AND c.usedCount < c.totalUsageLimit AND c.startDate <= :now AND c.endDate >= :now")
    Optional<Coupon> findValidByCode(@Param("code") String code, @Param("now") LocalDateTime now);
}