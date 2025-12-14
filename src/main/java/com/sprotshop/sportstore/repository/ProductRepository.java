package com.sprotshop.sportstore.repository;

import com.sprotshop.sportstore.entity.Product;
import com.sprotshop.sportstore.entity.ProductCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    // Make sure to include "productSizes" in attributePaths where needed

    @EntityGraph(attributePaths = {"productCategory", "images", "productSizes"})
    List<Product> findByNameContainingIgnoreCase(String name);

    @EntityGraph(attributePaths = {"images", "productSizes"}) // Assuming category details are not always needed here, adjust as necessary
    List<Product> findByProductCategory(ProductCategory productCategory);

    @Override
    @EntityGraph(attributePaths = {"productCategory", "images", "productSizes"})
    List<Product> findAll();

    @Override
    @EntityGraph(attributePaths = {"productCategory", "images", "productSizes"})
    Page<Product> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"productCategory", "images", "productSizes"})
    Optional<Product> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"productCategory", "images", "productSizes"}) // Add productSizes
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.id IN :productIds")
    List<Product> findAllByIdIn(@Param("productIds") List<Long> productIds);
}