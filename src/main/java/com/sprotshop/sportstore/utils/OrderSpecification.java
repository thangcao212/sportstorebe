package com.sprotshop.sportstore.utils;

import com.sprotshop.sportstore.entity.Address;
import com.sprotshop.sportstore.entity.Order;
import com.sprotshop.sportstore.entity.OrderItem;
import com.sprotshop.sportstore.request.OrderSearchRequest;
import com.sprotshop.sportstore.request.SearchOrderRequest;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class OrderSpecification {

    public static Specification<Order> filterOrders(OrderSearchRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Filter by min total amount
            if (request.getMinTotalAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("totalAmount"),
                        new BigDecimal(request.getMinTotalAmount())));
            }

            // Filter by max total amount
            if (request.getMaxTotalAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("totalAmount"),
                        new BigDecimal(request.getMaxTotalAmount())));
            }

            // Filter by order status
            if (request.getStatus() != null && !request.getStatus().isEmpty()) {
                predicates.add(root.get("status").in(request.getStatus()));
            }

            // Join with Address for location-based filtering
            Join<Order, Address> addressJoin = root.join("address", JoinType.LEFT);

            // Filter by province code
            if (request.getProvinceCode() != null) {
                predicates.add(cb.equal(addressJoin.get("provinceCode"), request.getProvinceCode()));
            }

            // Filter by district code
            if (request.getDistrictCode() != null) {
                predicates.add(cb.equal(addressJoin.get("districtCode"), request.getDistrictCode()));
            }

            // Filter by ward code
            if (request.getWardCode() != null) {
                predicates.add(cb.equal(addressJoin.get("wardCode"), request.getWardCode()));
            }

            // Search by address (street or full address) - case-insensitive
            if (StringUtils.hasText(request.getSearch())) {
                String keyword = "%" + request.getSearch().toLowerCase() + "%";
                List<Predicate> addressPredicates = new ArrayList<>();
                addressPredicates.add(cb.like(cb.lower(addressJoin.get("street")), keyword));
                addressPredicates.add(cb.like(cb.lower(addressJoin.get("fullAddress")), keyword));
                predicates.add(cb.or(addressPredicates.toArray(new Predicate[0])));
            }

            // Search by recipient name or phone - case-insensitive
            if (StringUtils.hasText(request.getSearch())) {
                String keyword = "%" + request.getSearch().toLowerCase() + "%";
                List<Predicate> recipientPredicates = new ArrayList<>();
                recipientPredicates.add(cb.like(cb.lower(root.get("shippingRecipientName")), keyword));
                recipientPredicates.add(cb.like(root.get("shippingPhone"), keyword)); // Phone không cần lower
                predicates.add(cb.or(recipientPredicates.toArray(new Predicate[0])));
            }

            // Filter by product ID (join with OrderItem)
            if (request.getProductId() != null) {
                Subquery<Long> subquery = query.subquery(Long.class);
                Root<OrderItem> orderItemRoot = subquery.from(OrderItem.class);
                Join<OrderItem, Order> orderJoin = orderItemRoot.join("order");
                subquery.select(orderJoin.get("id"));
                subquery.where(
                        cb.equal(orderItemRoot.get("product").get("id"), request.getProductId()),
                        cb.equal(orderJoin.get("id"), root.get("id"))
                );
                predicates.add(cb.exists(subquery));
            }

            // Filter by date range
            if (request.getStartDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), request.getStartDate()));
            }

            if (request.getEndDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), request.getEndDate()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}