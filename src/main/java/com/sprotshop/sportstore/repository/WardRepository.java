package com.sprotshop.sportstore.repository;

import com.sprotshop.sportstore.entity.Ward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WardRepository extends JpaRepository<Ward, Integer> {
    @Query("SELECT w FROM Ward w WHERE w.province.code = :provinceCode")
    List<Ward> findByProvinceCode(@Param("provinceCode") int provinceCode);
}