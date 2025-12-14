package com.sprotshop.sportstore.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sprotshop.sportstore.response.ApiResponse;
import com.sprotshop.sportstore.service.OrderStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderStatsController {

    private final OrderStatsService orderStatsService;

    @GetMapping("/daily-transactions")
    public ApiResponse<Map<Integer, Long>> getDailyTransactions(
            @RequestParam int month,
            @RequestParam int year
    ) {
        Map<Integer, Long> result = orderStatsService.getDailyTransactions(month, year);
        return ApiResponse.<Map<Integer, Long>>builder()
                .status(200)
                .message("Lấy thống kê giao dịch theo ngày thành công")
                .data(result)
                .build();
    }

    // 👈 NEW: Daily Profit
    @GetMapping("/daily-profit")
    public ApiResponse<Map<Integer, BigDecimal>> getDailyProfit(
            @RequestParam int month,
            @RequestParam int year
    ) {
        Map<Integer, BigDecimal> result = orderStatsService.getDailyProfit(month, year);
        return ApiResponse.<Map<Integer, BigDecimal>>builder()
                .status(200)
                .message("Lấy thống kê lợi nhuận theo ngày thành công")
                .data(result)
                .build();
    }

    @GetMapping("/weekly-transactions")
    public ApiResponse<Map<Integer, Long>> getWeeklyTransactions() {
        Map<Integer, Long> result = orderStatsService.getWeeklyTransactions();
        return ApiResponse.<Map<Integer, Long>>builder()
                .status(200)
                .message("Lấy thống kê giao dịch theo tuần thành công")
                .data(result)
                .build();
    }

    // 👈 NEW: Weekly Profit
    @GetMapping("/weekly-profit")
    public ApiResponse<Map<Integer, BigDecimal>> getWeeklyProfit() {
        Map<Integer, BigDecimal> result = orderStatsService.getWeeklyProfit();
        return ApiResponse.<Map<Integer, BigDecimal>>builder()
                .status(200)
                .message("Lấy thống kê lợi nhuận theo tuần thành công")
                .data(result)
                .build();
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> getSummary() {
        return ApiResponse.<Map<String, Object>>builder()
                .message("Thống kê KPI")
                .status(200)
                .data(orderStatsService.getSummary())
                .build();
    }

    @GetMapping("/status-ratio")
    public ApiResponse<Map<String, Long>> getOrderStatusRatio() {
        return ApiResponse.<Map<String, Long>>builder()
                .status(200)
                .message("Tỷ lệ đơn hàng theo trạng thái")
                .data(orderStatsService.getOrderStatusRatio())
                .build();
    }

    @GetMapping("/monthly-revenue")
    public ApiResponse<Map<Integer, Double>> getMonthlyRevenue(
            @RequestParam int year
    ) {
        return ApiResponse.<Map<Integer, Double>>builder()
                .status(200)
                .message("Doanh thu theo tháng")
                .data(orderStatsService.getMonthlyRevenue(year))
                .build();
    }

    // 👈 NEW: Monthly Profit
    @GetMapping("/monthly-profit")
    public ApiResponse<Map<Integer, BigDecimal>> getMonthlyProfit(
            @RequestParam int year
    ) {
        Map<Integer, BigDecimal> result = orderStatsService.getMonthlyProfit(year);
        return ApiResponse.<Map<Integer, BigDecimal>>builder()
                .status(200)
                .message("Lợi nhuận theo tháng")
                .data(result)
                .build();
    }

    // 👈 NEW: Yearly Profit
    @GetMapping("/yearly-profit")
    public ApiResponse<Map<Integer, BigDecimal>> getYearlyProfit(
            @RequestParam(defaultValue = "5") int yearsBack
    ) {
        Map<Integer, BigDecimal> result = orderStatsService.getYearlyProfit(yearsBack);
        return ApiResponse.<Map<Integer, BigDecimal>>builder()
                .status(200)
                .message("Lợi nhuận theo năm")
                .data(result)
                .build();
    }

    @GetMapping("/top-products")
    public ApiResponse<List<Map<String, Object>>> getTopProducts(
            @RequestParam(defaultValue = "5") int limit
    ) {
        return ApiResponse.<List<Map<String, Object>>>builder()
                .status(200)
                .message("Top sản phẩm bán chạy")
                .data(orderStatsService.getTopProducts(limit))
                .build();
    }

    @GetMapping("/worst-products")
    public ApiResponse<List<Map<String, Object>>> getWorstProducts(
            @RequestParam(defaultValue = "5") int limit
    ) {
        return ApiResponse.<List<Map<String, Object>>>builder()
                .status(200)
                .message("Top sản phẩm bán kém nhất")
                .data(orderStatsService.getWorstProducts(limit))
                .build();
    }

    // 👈 NEW: Top Products by Profit
    @GetMapping("/top-products-by-profit")
    public ApiResponse<List<Map<String, Object>>> getTopProductsByProfit(
            @RequestParam(defaultValue = "5") int limit
    ) {
        return ApiResponse.<List<Map<String, Object>>>builder()
                .status(200)
                .message("Top sản phẩm theo lợi nhuận")
                .data(orderStatsService.getTopProductsByProfit(limit))
                .build();
    }
}