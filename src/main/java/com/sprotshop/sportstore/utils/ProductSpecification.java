package com.sprotshop.sportstore.utils;

import com.sprotshop.sportstore.entity.Product;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

public class ProductSpecification {


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


    public static Specification<Product> byCostPriceRange(Double minCostPrice, Double maxCostPrice) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (minCostPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("costPrice"), minCostPrice));
            }
            if (maxCostPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("costPrice"), maxCostPrice));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }


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


    public static Specification<Product> byBrandId(Long brandId) {
        return (root, query, cb) -> {
            if (brandId == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("brand").get("id"), brandId);
        };
    }


    public static Specification<Product> fetchBrand() {
        return (root, query, cb) -> {

            if (!Long.class.equals(query.getResultType())) {
                root.fetch("brand", JoinType.LEFT);
            }

            return cb.conjunction();  // Không thêm where clause
        };
    }

    // Optional: Nếu cần fetch sizes/images luôn (tránh empty list nếu lazy)
    public static Specification<Product> fetchSizesAndImages() {
        return (root, query, cb) -> {
            if (!Long.class.equals(query.getResultType())) {
                root.fetch("productSizes", JoinType.LEFT);
                root.fetch("images", JoinType.LEFT);
            }
            return cb.conjunction();
        };
    }
}