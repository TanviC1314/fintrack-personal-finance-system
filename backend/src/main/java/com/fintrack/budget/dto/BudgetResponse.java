package com.fintrack.budget.dto;

import com.fintrack.budget.entity.Budget;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * A budget together with how much of it has been used.
 *
 * @param spent              non-reversed expenses in this category, month and currency
 * @param remaining          amount minus spent; negative once the budget is exceeded
 * @param utilisationPercent spent as a percentage of amount, one decimal place; may exceed 100
 * @param exceeded           true only when spent is strictly more than the amount
 */
public record BudgetResponse(
        UUID id,
        UUID categoryId,
        String categoryName,
        String month,
        String currency,
        BigDecimal amount,
        BigDecimal spent,
        BigDecimal remaining,
        BigDecimal utilisationPercent,
        boolean exceeded) {

    public static BudgetResponse of(Budget budget, BigDecimal spent) {
        BigDecimal amount = budget.getAmount();
        BigDecimal utilisation = spent.multiply(BigDecimal.valueOf(100)).divide(amount, 1, RoundingMode.HALF_UP);
        return new BudgetResponse(
                budget.getId(),
                budget.getCategory().getId(),
                budget.getCategory().getName(),
                budget.getMonth().toString().substring(0, 7),
                budget.getCurrency(),
                amount,
                spent,
                amount.subtract(spent),
                utilisation,
                spent.compareTo(amount) > 0);
    }
}
