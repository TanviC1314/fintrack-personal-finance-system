package com.fintrack.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Full replacement of the editable fields. The account and type of a transaction never change. */
public record UpdateTransactionRequest(

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        @Digits(integer = 17, fraction = 2, message = "Amount must have at most 17 digits and 2 decimal places")
        BigDecimal amount,

        @Size(max = 50, message = "Category must be at most 50 characters")
        String category,

        @Size(max = 255, message = "Description must be at most 255 characters")
        String description,

        @NotNull(message = "Transaction date is required")
        LocalDate transactionDate) {
}
