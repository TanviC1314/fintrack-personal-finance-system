package com.fintrack.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Where one month's money went, largest category first.
 *
 * @param categories uncategorised spending appears as one entry with a null id
 */
public record CategorySpendingReport(String currency, String month, BigDecimal totalSpent, List<Entry> categories) {

    /** @param percentage share of the month's total spending, one decimal place */
    public record Entry(UUID categoryId, String categoryName, BigDecimal amount, BigDecimal percentage) {
    }
}
