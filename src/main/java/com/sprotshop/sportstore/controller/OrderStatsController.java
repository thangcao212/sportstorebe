package com.sprotshop.sportstore.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sprotshop.sportstore.response.ApiResponse;
import com.sprotshop.sportstore.service.OrderStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


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

    @GetMapping("/weekly-transactions")
    public ApiResponse<Map<Integer, Long>> getWeeklyTransactions() {
        Map<Integer, Long> result = orderStatsService.getWeeklyTransactions();
        return ApiResponse.<Map<Integer, Long>>builder()
                .status(200)
                .message("Lấy thống kê giao dịch theo tuần thành công")
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

    //
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

}
