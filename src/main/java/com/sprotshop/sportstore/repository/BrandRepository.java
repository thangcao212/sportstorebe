package com.sprotshop.sportstore.repository;

import com.sprotshop.sportstore.entity.Brand;
import com.sprotshop.sportstore.entity.Image;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BrandRepository extends JpaRepository<Brand, Long> {
    Optional<Brand> findByNameIgnoreCase(String name);
}
