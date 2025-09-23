package com.sprotshop.sportstore.utils;

import com.sprotshop.sportstore.entity.Address;
import com.sprotshop.sportstore.entity.Order;
import com.sprotshop.sportstore.entity.OrderItem;
import com.sprotshop.sportstore.request.OrderSearchRequest;
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

            // Validate tổng tiền
            if (request.getMinTotalAmount() != null && request.getMaxTotalAmount() != null) {
                if (request.getMaxTotalAmount().compareTo(request.getMinTotalAmount()) < 0) {
                    throw new IllegalArgumentException("Max total amount không thể nhỏ hơn Min total amount");
                }
            }

            // Validate ngày
            if (request.getStartDate() != null && request.getEndDate() != null) {
                if (request.getEndDate().isBefore(request.getStartDate())) {
                    throw new IllegalArgumentException("Ngày kết thúc không thể nhỏ hơn ngày bắt đầu");
                }
            }

            // Min / Max total
            if (request.getMinTotalAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("totalAmount"),
                        new BigDecimal(request.getMinTotalAmount().toString())
                ));
            }
            if (request.getMaxTotalAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("totalAmount"),
                        new BigDecimal(request.getMaxTotalAmount().toString())
                ));
            }

            // Status
            if (request.getStatus() != null && !request.getStatus().isEmpty()) {
                predicates.add(root.get("status").in(request.getStatus()));
            }

            // Join Address
            Join<Order, Address> addressJoin = root.join("address", JoinType.LEFT);

            if (request.getProvinceCode() != null) {
                predicates.add(cb.equal(addressJoin.get("provinceCode"), request.getProvinceCode()));
            }
            if (request.getDistrictCode() != null) {
                predicates.add(cb.equal(addressJoin.get("districtCode"), request.getDistrictCode()));
            }
            if (request.getWardCode() != null) {
                predicates.add(cb.equal(addressJoin.get("wardCode"), request.getWardCode()));
            }

            // Search (OR all fields)
            if (StringUtils.hasText(request.getSearch())) {
                String keyword = "%" + request.getSearch().toLowerCase() + "%";
                List<Predicate> searchPreds = new ArrayList<>();
                searchPreds.add(cb.like(cb.lower(addressJoin.get("street")), keyword));
                searchPreds.add(cb.like(cb.lower(addressJoin.get("fullAddress")), keyword));
                searchPreds.add(cb.like(cb.lower(root.get("shippingRecipientName")), keyword));
                searchPreds.add(cb.like(cb.lower(root.get("shippingPhone")), keyword));
                predicates.add(cb.or(searchPreds.toArray(new Predicate[0])));
            }

            // Filter by productId
            if (request.getProductId() != null) {
                Subquery<Long> sub = query.subquery(Long.class);
                Root<OrderItem> oi = sub.from(OrderItem.class);
                sub.select(oi.get("order").get("id"));
                sub.where(
                        cb.equal(oi.get("product").get("id"), request.getProductId()),
                        cb.equal(oi.get("order").get("id"), root.get("id"))
                );
                predicates.add(cb.exists(sub));
            }

            // Date range
            if (request.getStartDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), request.getStartDate()));
            }
            if (request.getEndDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), request.getEndDate()));
            }

            query.distinct(true);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

}