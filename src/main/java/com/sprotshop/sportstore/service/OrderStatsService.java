package com.sprotshop.sportstore.service;

import com.sprotshop.sportstore.Enum.OrderStatus;
import com.sprotshop.sportstore.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderStatsService {
    private final OrderRepository orderRepository;

    public Map<Integer, Long> getDailyTransactions(int month, int year) {
        List<Object[]> results = orderRepository.countOrdersByDay(month, year);

        Map<Integer, Long> map = new LinkedHashMap<>();
        YearMonth yearMonth = YearMonth.of(year, month);

        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            map.put(day, 0L);
        }

        for (Object[] row : results) {
            Integer day = ((Number) row[0]).intValue();
            Long count = ((Number) row[1]).longValue();
            map.put(day, count);
        }

        return map;
    }

    public Map<Integer, BigDecimal> getDailyProfit(int month, int year) {
        List<Object[]> results = orderRepository.sumProfitByDay(month, year);

        Map<Integer, BigDecimal> map = new LinkedHashMap<>();
        YearMonth yearMonth = YearMonth.of(year, month);

        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            map.put(day, BigDecimal.ZERO);
        }

        for (Object[] row : results) {
            Integer day = ((Number) row[0]).intValue();
            BigDecimal profit = (BigDecimal) row[1];
            map.put(day, profit != null ? profit : BigDecimal.ZERO);
        }

        return map;
    }



    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalOrders", orderRepository.count());

        // Doanh thu thực thu của khách (vẫn giữ nguyên để hiển thị)
        BigDecimal totalRevenue = orderRepository.sumTotalAmount();
        summary.put("totalRevenue", totalRevenue != null ? totalRevenue : BigDecimal.ZERO);

        // XÓA 2 DÒNG CŨ NÀY ĐI (không cần nữa)
        // BigDecimal totalCost = orderRepository.sumTotalCost();
        // summary.put("totalCost", totalCost != null ? totalCost : BigDecimal.ZERO);

        // DÙNG CÁI MỚI – LỢI NHUẬN CHUẨN KẾ TOÁN
        BigDecimal totalProfit = orderRepository.sumTotalProfit();
        summary.put("totalProfit", totalProfit != null ? totalProfit : BigDecimal.ZERO);

        summary.put("totalCanceled", orderRepository.countByStatus(OrderStatus.CANCELED));
        summary.put("totalCompleted", orderRepository.countByStatus(OrderStatus.COMPLETED));

        return summary;
    }
    public Map<String, Long> getOrderStatusRatio() {
        List<Object[]> results = orderRepository.countOrdersByStatus();
        Map<String, Long> map = new HashMap<>();
        for (Object[] row : results) {
            String status = row[0].toString();
            Long count = ((Number) row[1]).longValue();
            map.put(status, count);
        }
        return map;
    }

    public Map<Integer, Double> getMonthlyRevenue(int year) {
        List<Object[]> results = orderRepository.sumRevenueByMonth(year);
        Map<Integer, Double> map = new LinkedHashMap<>();
        for (int m = 1; m <= 12; m++) {
            map.put(m, 0.0);
        }
        for (Object[] row : results) {
            Integer month = ((Number) row[0]).intValue();
            Double revenue = ((Number) row[1]).doubleValue();
            map.put(month, revenue);
        }
        return map;
    }

    public Map<Integer, BigDecimal> getMonthlyProfit(int year) {
        List<Object[]> results = orderRepository.sumProfitByMonth(year);

        Map<Integer, BigDecimal> map = new LinkedHashMap<>();
        for (int m = 1; m <= 12; m++) {
            map.put(m, BigDecimal.ZERO);
        }

        for (Object[] row : results) {
            Integer month = ((Number) row[0]).intValue();
            BigDecimal profit = (BigDecimal) row[1];
            map.put(month, profit != null ? profit : BigDecimal.ZERO);
        }

        return map;
    }


    public List<Map<String, Object>> getTopProducts(int limit) {
        List<Object[]> results = orderRepository.findTopProducts(PageRequest.of(0, limit));
        return results.stream().map(row -> {
            Map<String, Object> map = new HashMap<>();
            map.put("productName", row[0]);
            map.put("totalSold", ((Number) row[1]).longValue());
            return map;
        }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getTopProductsByProfit(int limit) {
        List<Object[]> results = orderRepository.findTopProductsByProfit(PageRequest.of(0, limit));
        return results.stream().map(row -> {
            Map<String, Object> map = new HashMap<>();
            map.put("productName", row[0]);
            BigDecimal profit = (BigDecimal) row[1];
            map.put("totalProfit", profit != null ? profit : BigDecimal.ZERO);
            return map;
        }).collect(Collectors.toList());
    }


    /// /
    public Map<Integer, Long> getWeeklyTransactions() {
        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.with(DayOfWeek.MONDAY);
        LocalDate endOfWeek = today.with(DayOfWeek.SUNDAY);

        List<Object[]> results = orderRepository.countOrdersByDateRange(startOfWeek, endOfWeek);  // 👈 FIXED: LocalDate thuần

        Map<Integer, Long> map = new LinkedHashMap<>();
        for (int i = 1; i <= 7; i++) {
            map.put(i, 0L);
        }

        for (Object[] row : results) {
            int mysqlDow = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();

            int mappedDow = (mysqlDow == 1) ? 7 : mysqlDow - 1;
            map.put(mappedDow, count);
        }

        return map;
    }

    public Map<Integer, BigDecimal> getWeeklyProfit() {
        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.with(DayOfWeek.MONDAY);
        LocalDate endOfWeek = today.with(DayOfWeek.SUNDAY);

        List<Object[]> results = orderRepository.sumProfitByDateRange(startOfWeek, endOfWeek);  // 👈 FIXED: LocalDate thuần

        Map<Integer, BigDecimal> map = new LinkedHashMap<>();
        for (int i = 1; i <= 7; i++) {
            map.put(i, BigDecimal.ZERO);
        }

        for (Object[] row : results) {
            int mysqlDow = ((Number) row[0]).intValue();
            BigDecimal profit = (BigDecimal) row[1];

            int mappedDow = (mysqlDow == 1) ? 7 : mysqlDow - 1;
            map.put(mappedDow, profit != null ? profit : BigDecimal.ZERO);
        }

        return map;
    }

    public Map<Integer, BigDecimal> getYearlyProfit(int yearsBack) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusYears(yearsBack);

        List<Object[]> results = orderRepository.sumProfitByYearRange(startDate, endDate);  // 👈 FIXED: LocalDate thuần

        Map<Integer, BigDecimal> map = new LinkedHashMap<>();
        for (int y = endDate.getYear(); y >= startDate.getYear(); y--) {
            map.put(y, BigDecimal.ZERO);
        }

        for (Object[] row : results) {
            Integer year = ((Number) row[0]).intValue();
            BigDecimal profit = (BigDecimal) row[1];
            map.put(year, profit != null ? profit : BigDecimal.ZERO);
        }

        return map;
    }
}