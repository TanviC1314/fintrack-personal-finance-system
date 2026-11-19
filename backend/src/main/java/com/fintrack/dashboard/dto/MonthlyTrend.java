package com.fintrack.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

/** Income against expenses for a run of consecutive months, oldest first. Months without activity are zero. */
public record MonthlyTrend(String currency, List<Point> months) {

    public record Point(String month, BigDecimal income, BigDecimal expenses, BigDecimal savings) {
    }
}
