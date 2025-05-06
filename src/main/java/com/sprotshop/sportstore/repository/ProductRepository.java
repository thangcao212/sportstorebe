package com.sprotshop.sportstore.repository;

import com.sprotshop.sportstore.entity.Product;
import com.sprotshop.sportstore.entity.ProductCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Product entity
 * Provides CRUD operations and custom query methods for Product entities
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {


    @EntityGraph(attributePaths = {"productCategory", "images"})
    List<Product> findByNameContainingIgnoreCase(String name);

    @EntityGraph(attributePaths = {"images"})
    List<Product> findByProductCategory(ProductCategory productCategory);

    @Override
    @EntityGraph(attributePaths = {"productCategory", "images"})
    List<Product> findAll();

    @Override
    @EntityGraph(attributePaths = {"productCategory", "images"})
    Page<Product> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"productCategory", "images"})
    Optional<Product> findById(Long id);
}