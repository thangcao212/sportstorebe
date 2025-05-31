package com.sprotshop.sportstore.utils;

import com.sprotshop.sportstore.entity.Order;
import com.sprotshop.sportstore.request.OrderSearchRequest;
import com.sprotshop.sportstore.service.LocationService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class OrderSpecification {

    private static LocationService locationService;

    @Autowired
    public OrderSpecification(LocationService locationService) {
        OrderSpecification.locationService = locationService;
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
            if (request.getShippingCity() != null && !request.getShippingCity().isEmpty()) {
                predicates.add(cb.equal(root.get("shippingCity"), request.getShippingCity()));
            }
            if (request.getShippingDistrict() != null && !request.getShippingDistrict().isEmpty()) {
                predicates.add(cb.equal(root.get("shippingDistrict"), request.getShippingDistrict()));
            }
            if (request.getShippingWard() != null && !request.getShippingWard().isEmpty()) {
                predicates.add(cb.equal(root.get("shippingWard"), request.getShippingWard()));
            }
            if (request.getWardCode() != null) {
                String wardName = locationService.getWardByCode(request.getWardCode()).getName();
                predicates.add(cb.equal(root.get("shippingWard"), wardName));
            }
            if (request.getTrackingNumber() != null && !request.getTrackingNumber().isEmpty()) {
                predicates.add(cb.equal(root.get("trackingNumber"), request.getTrackingNumber()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}