package com.sprotshop.sportstore.service;

import com.sprotshop.sportstore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserStatsService {
    private final UserRepository userRepository;

    // Thống kê theo ngày
    public Map<Integer, Long> getNewUsersByDay(int month, int year) {
        List<Object[]> results = userRepository.countNewUsersByDay(month, year);

        Map<Integer, Long> map = new LinkedHashMap<>();
        int daysInMonth = YearMonth.of(year, month).lengthOfMonth();
        for (int d = 1; d <= daysInMonth; d++) {
            map.put(d, 0L);
        }

        for (Object[] row : results) {
            int day = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            map.put(day, count);
        }

        return map;
    }

    // Thống kê theo tháng
    public Map<Integer, Long> getNewUsersByMonth(int year) {
        List<Object[]> results = userRepository.countNewUsersByMonth(year);

        Map<Integer, Long> map = new LinkedHashMap<>();
        for (int m = 1; m <= 12; m++) {
            map.put(m, 0L);
        }

        for (Object[] row : results) {
            int month = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            map.put(month, count);
        }

        return map;
    }

    // Thống kê theo năm
    public Map<Integer, Long> getNewUsersByYear() {
        List<Object[]> results = userRepository.countNewUsersByYear();

        Map<Integer, Long> map = new LinkedHashMap<>();
        for (Object[] row : results) {
            int year = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            map.put(year, count);
        }

        return map;
    }
}
