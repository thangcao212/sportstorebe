package com.sprotshop.sportstore.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sprotshop.sportstore.Enum.OrderStatus;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderSearchRequest {
    private Double minTotalAmount;
    private Double maxTotalAmount;
    private List<OrderStatus> status;
    private Integer provinceCode;
    private Integer districtCode;
    private Integer wardCode;
    private String search;
    private Long productId;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}