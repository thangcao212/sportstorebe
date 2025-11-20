package com.sprotshop.sportstore.repository;

import com.sprotshop.sportstore.entity.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    List<ProductCategory> findByParentIsNull();

    List<ProductCategory> findByParent(ProductCategory parent);

    @Query("SELECT c FROM ProductCategory c WHERE c.name = :normalizedName AND ((:parentId IS NULL AND c.parent IS NULL) OR (:parentId IS NOT NULL AND c.parent.id = :parentId))")
    Optional<ProductCategory> findByNameAndParentId(@Param("normalizedName") String normalizedName, @Param("parentId") Long parentId);

    @Query(value = "WITH RECURSIVE category_tree AS (" +
            "  SELECT id FROM product_category WHERE id = :categoryId " +
            "  UNION ALL " +
            "  SELECT c.id FROM product_category c " +
            "  JOIN category_tree ct ON c.parent_id = ct.id " +
            ") SELECT id FROM category_tree", nativeQuery = true)
    List<Long> findAllDescendantIds(@Param("categoryId") Long categoryId);

    @Query(value = """
        WITH RECURSIVE category_tree AS (
            SELECT id FROM product_category WHERE id = :categoryId
            UNION ALL
            SELECT c.id FROM product_category c
            INNER JOIN category_tree ct ON c.parent_id = ct.id
        )
        SELECT COUNT(*) FROM product p
        INNER JOIN category_tree ct ON p.category_id = ct.id
        """, nativeQuery = true)
    long countProductsInSubtree(@Param("categoryId") Long categoryId);
}