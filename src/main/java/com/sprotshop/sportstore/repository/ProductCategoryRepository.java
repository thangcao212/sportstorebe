package com.sprotshop.sportstore.repository;

import com.sprotshop.sportstore.entity.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for ProductCategory entity
 * Provides CRUD operations and custom query methods for ProductCategory entities
 */
@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    List<ProductCategory> findByParentIsNull();

    List<ProductCategory> findByParent(ProductCategory parent);

    @Query("SELECT c FROM ProductCategory c WHERE c.name = :normalizedName AND ((:parentId IS NULL AND c.parent IS NULL) OR (:parentId IS NOT NULL AND c.parent.id = :parentId))")
    Optional<ProductCategory> findByNameAndParentId(@Param("normalizedName") String normalizedName, @Param("parentId") Long parentId);

}