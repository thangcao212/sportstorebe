package com.sprotshop.sportstore.utils;

import com.sprotshop.sportstore.entity.Order;
import com.sprotshop.sportstore.entity.Ward;
import com.sprotshop.sportstore.repository.WardRepository;
import com.sprotshop.sportstore.request.OrderSearchRequest;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class OrderSpecification {

    private static WardRepository wardRepository;

    @Autowired
    public OrderSpecification(WardRepository wardRepository) {
        OrderSpecification.wardRepository = wardRepository;
    }

    public static Specification<Order> buildSearchSpecification(OrderSearchRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.getOrderId() != null) {
                predicates.add(cb.equal(root.get("id"), request.getOrderId()));
            }
            if (request.getUserId() != null) {
                predicates.add(cb.equal(root.get("user").get("id"), request.getUserId()));
            }
            if (request.getUserEmail() != null && !request.getUserEmail().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("user").get("email")), "%" + request.getUserEmail().toLowerCase() + "%"));
            }
            if (request.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), request.getStatus()));
            }
            if (request.getPaymentMethod() != null) {
                predicates.add(cb.equal(root.get("paymentMethod"), request.getPaymentMethod()));
            }
            if (request.getPaymentStatus() != null && !request.getPaymentStatus().isEmpty()) {
                predicates.add(cb.equal(root.get("paymentStatus"), request.getPaymentStatus()));
            }
            if (request.getCreatedAtFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), request.getCreatedAtFrom()));
            }
            if (request.getCreatedAtTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), request.getCreatedAtTo()));
            }
            if (request.getMinTotalAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("totalAmount"), request.getMinTotalAmount()));
            }
            if (request.getMaxTotalAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("totalAmount"), request.getMaxTotalAmount()));
            }
            if (request.getProvinceCode() != null) {
                predicates.add(cb.equal(root.get("address").get("provinceCode"), request.getProvinceCode()));
            }
            if (request.getDistrictCode() != null) {
                predicates.add(cb.equal(root.get("address").get("districtCode"), request.getDistrictCode()));
            }
            if (request.getWardCode() != null) {
                Ward ward = wardRepository.findById(request.getWardCode())
                        .orElseThrow(() -> new IllegalArgumentException("Invalid ward code: " + request.getWardCode()));
                predicates.add(cb.equal(root.get("address").get("fullAddress"), ward.getName()));
            }
            if (request.getTrackingNumber() != null && !request.getTrackingNumber().isEmpty()) {
                predicates.add(cb.equal(root.get("trackingNumber"), request.getTrackingNumber()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}