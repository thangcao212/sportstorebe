// src/main/java/com/sprotshop/sportstore/dto/DateRange.java
package com.sprotshop.sportstore.request;

import java.time.LocalDate;

public record DateRange(
        LocalDate start,
        LocalDate end,
        String label
) {
    public static DateRange of(LocalDate start, LocalDate end, String label) {
        return new DateRange(start, end, label);
    }
}