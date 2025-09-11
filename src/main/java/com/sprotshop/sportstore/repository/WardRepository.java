package com.sprotshop.sportstore.repository;

import com.sprotshop.sportstore.entity.Ward;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WardRepository extends JpaRepository<Ward, Integer> {
    List<Ward> findByDistrictCode(int districtCode);
}