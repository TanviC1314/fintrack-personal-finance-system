package com.fintrack.dashboard.dto;

import com.fintrack.transaction.dto.TransactionResponse;
import java.math.BigDecimal;
import java.util.List;

/**
 * The headline numbers for one month and one currency.
 *
 * @param currency      null only when the caller has no accounts at all
 * @param month         the month the income and expense figures cover, as yyyy-MM
 * @param totalBalance  the sum of the active accounts' balances right now (not as of the month)
 * @param monthlySavings income minus expenses; negative when more was spent than earned
 */
public record DashboardSummary(
        String currency,
        String month,
        BigDecimal totalBalance,
        BigDecimal monthlyIncome,
        BigDecimal monthlyExpenses,
        BigDecimal monthlySavings,
        List<TransactionResponse> recentTransactions) {
}
