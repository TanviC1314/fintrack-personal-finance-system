package com.fintrack.budget.dto;

import com.fintrack.common.validation.CurrencyCode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

/**
 * @param month    the month the budget applies to, as yyyy-MM
 * @param currency only spending from accounts in this currency counts against the budget
 */
public record CreateBudgetRequest(

        @NotNull(message = "Category is required")
        UUID categoryId,

        @NotNull(message = "Month is required")
        YearMonth month,

        @NotNull(message = "Currency is required")
        @CurrencyCode
        String currency,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        @Digits(integer = 17, fraction = 2, message = "Amount must have at most 17 digits and 2 decimal places")
        BigDecimal amount) {
}
