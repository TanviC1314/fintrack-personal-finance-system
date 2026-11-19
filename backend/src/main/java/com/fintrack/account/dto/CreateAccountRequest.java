package com.fintrack.account.dto;

import com.fintrack.account.entity.AccountType;
import com.fintrack.common.validation.CurrencyCode;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * @param openingBalance optional; defaults to zero. At most two decimal places, matching NUMERIC(19,2).
 */
public record CreateAccountRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @NotNull(message = "Account type is required")
        AccountType type,

        @NotNull(message = "Currency is required")
        @CurrencyCode
        String currency,

        @PositiveOrZero(message = "Opening balance cannot be negative")
        @Digits(integer = 17, fraction = 2, message = "Opening balance must have at most 17 digits and 2 decimal places")
        BigDecimal openingBalance) {
}
