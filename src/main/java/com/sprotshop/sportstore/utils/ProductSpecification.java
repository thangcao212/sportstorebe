package com.sprotshop.sportstore.utils;

import com.sprotshop.sportstore.entity.Product;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

public class ProductSpecification {

    /**
     * Tìm kiếm theo tên hoặc mô tả
     */
    public static Specification<Product> bySearchValue(String searchValue) {
        return (root, query, cb) -> {
            if (searchValue == null || searchValue.trim().isEmpty()) {
                return cb.conjunction();
            }

            String keyword = "%" + searchValue.toLowerCase() + "%";
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.like(cb.lower(root.get("name")), keyword));
            predicates.add(cb.like(cb.lower(root.get("description")), keyword));

            return cb.or(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Lọc theo ID danh mục sản phẩm, bao gồm danh mục cha và tất cả danh mục con đệ quy
     * @param categoryId ID của danh mục cha
     * @param descendantIds Danh sách ID của tất cả danh mục con đệ quy
     */
    public static Specification<Product> byCategoryId(Long categoryId, List<Long> descendantIds) {
        return (root, query, cb) -> {
            if (categoryId == null && (descendantIds == null || descendantIds.isEmpty())) {
                return cb.conjunction();
            }

            List<Long> categoryIds = new ArrayList<>();
            if (categoryId != null) {
                categoryIds.add(categoryId); // Bao gồm danh mục cha
            }
            if (descendantIds != null) {
                categoryIds.addAll(descendantIds); // Bao gồm tất cả danh mục con
            }

            if (categoryIds.isEmpty()) {
                return cb.conjunction();
            }

            return root.get("productCategory").get("id").in(categoryIds);
        };
    }

    /**
     * Lọc theo khoảng giá
     */
    public static Specification<Product> byPriceRange(Double minPrice, Double maxPrice) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Lọc theo tồn kho
     */
    public static Specification<Product> byStockQuantity(Integer minStock, Integer maxStock) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (minStock != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("stockQuantity"), minStock));
            }
            if (maxStock != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("stockQuantity"), maxStock));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}