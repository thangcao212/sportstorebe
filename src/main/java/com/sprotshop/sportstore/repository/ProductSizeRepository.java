package com.sprotshop.sportstore.repository;

import com.sprotshop.sportstore.entity.Product;
import com.sprotshop.sportstore.entity.ProductSize;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductSizeRepository extends JpaRepository<ProductSize, Long> {

    Optional<ProductSize> findByProductAndSize(Product product, String size);

}
