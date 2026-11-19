package com.fintrack.budget.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Only the amount can change; to budget another category, month or currency, create a new budget. */
public record UpdateBudgetRequest(

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        @Digits(integer = 17, fraction = 2, message = "Amount must have at most 17 digits and 2 decimal places")
        BigDecimal amount) {
}
