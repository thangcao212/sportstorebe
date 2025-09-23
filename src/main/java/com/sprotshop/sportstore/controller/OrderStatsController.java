package com.sprotshop.sportstore.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sprotshop.sportstore.response.ApiResponse;
import com.sprotshop.sportstore.service.OrderStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;



import com.fasterxml.jackson.annotation.JsonInclude;
import com.sprotshop.sportstore.service.OrderStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
}
