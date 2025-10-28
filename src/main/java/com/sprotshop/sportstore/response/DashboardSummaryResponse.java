package com.sprotshop.sportstore.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class DashboardSummaryResponse {
    private Long totalOrders;
    private BigDecimal totalRevenue;
    private BigDecimal totalCost; // 👈 Added
    private BigDecimal totalProfit; // 👈 Added
    private Long totalCanceled;
    private Long totalCompleted;
}