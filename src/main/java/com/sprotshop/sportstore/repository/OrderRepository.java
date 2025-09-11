
package com.sprotshop.sportstore.repository;

import com.sprotshop.sportstore.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

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
}
