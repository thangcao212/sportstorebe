
package com.sprotshop.sportstore.repository;

import com.sprotshop.sportstore.Enum.OrderStatus;
import com.sprotshop.sportstore.entity.Order;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
    Page<Order> findByUserId(Long userId, Pageable pageable);
    Optional<Order> findByIdAndUserId(Long id, Long userId);



    boolean existsByUser_Id(Long id);

    List<Order> findByAddressProvinceCode(int provinceCode);
    List<Order> findByAddressDistrictCode(int districtCode);
    List<Order> findByAddressWardCode(int wardCode);

    @Query("SELECT a.provinceCode, COUNT(o) FROM Order o JOIN o.address a GROUP BY a.provinceCode")
    List<Object[]> countOrdersByProvince();

    @Query("SELECT a.districtCode, COUNT(o) FROM Order o JOIN o.address a GROUP BY a.districtCode")
    List<Object[]> countOrdersByDistrict();

    @Query("SELECT a.wardCode, COUNT(o) FROM Order o JOIN o.address a GROUP BY a.wardCode")
    List<Object[]> countOrdersByWard();

    // Daily transactions trong 1 tháng/năm
    @Query("SELECT DAY(o.createdAt) as day, COUNT(o) as cnt " +
            "FROM Order o " +
            "WHERE MONTH(o.createdAt) = :month AND YEAR(o.createdAt) = :year " +
            "GROUP BY DAY(o.createdAt) ORDER BY day")
    List<Object[]> countOrdersByDay(@Param("month") int month, @Param("year") int year);

    // Weekly transactions trong khoảng ngày (dùng MySQL DAYOFWEEK)
    @Query(value = "SELECT DAYOFWEEK(o.created_at) as dow, COUNT(*) as cnt " +
            "FROM orders o " +
            "WHERE DATE(o.created_at) BETWEEN :start AND :end " +
            "GROUP BY DAYOFWEEK(o.created_at) ORDER BY dow",
            nativeQuery = true)
    List<Object[]> countOrdersByDateRange(@Param("start") LocalDate start, @Param("end") LocalDate end);

    // KPI
    @Query("SELECT COALESCE(SUM(o.totalAmount),0) FROM Order o")
    Long sumTotalAmount();

    Long countByStatus(OrderStatus status);
}
