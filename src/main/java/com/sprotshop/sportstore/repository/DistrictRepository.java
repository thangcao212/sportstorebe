package com.sprotshop.sportstore.repository;

import com.sprotshop.sportstore.entity.District;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DistrictRepository extends JpaRepository<District, Integer> {
    List<District> findByProvinceCode(int provinceCode);
}
