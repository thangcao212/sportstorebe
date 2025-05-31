
package com.sprotshop.sportstore.request;

import com.sprotshop.sportstore.Enum.OrderStatus;
import com.sprotshop.sportstore.Enum.PaymentMethod;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderSearchRequest {
    private Long orderId;
    private Long userId;
    private String userEmail;
    private OrderStatus status;
    private PaymentMethod paymentMethod;
    private String paymentStatus;
    private LocalDateTime createdAtFrom;
    private LocalDateTime createdAtTo;
    private BigDecimal minTotalAmount;
    private BigDecimal maxTotalAmount;
    private String shippingCity;
    private String shippingDistrict;
    private String shippingWard;
    private String trackingNumber;

    private Integer provinceCode;
    private Integer districtCode;
    private Integer wardCode;
}
