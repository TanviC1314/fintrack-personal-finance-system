package com.fintrack.transaction.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fintrack.transaction.entity.TransactionType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * @param type INCOME or EXPENSE. Transfers are created through the transfer endpoint only.
 */
public record CreateTransactionRequest(

        @NotNull(message = "Account is required")
        UUID accountId,

        @NotNull(message = "Transaction type is required")
        TransactionType type,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        @Digits(integer = 17, fraction = 2, message = "Amount must have at most 17 digits and 2 decimal places")
        BigDecimal amount,

        UUID categoryId,

        @Size(max = 255, message = "Description must be at most 255 characters")
        String description,

        @NotNull(message = "Transaction date is required")
        LocalDate transactionDate) {

    @JsonIgnore
    @AssertTrue(message = "Type must be INCOME or EXPENSE; use the transfer endpoint to move money between accounts")
    boolean isManualType() {
        return type == null || !type.isTransfer();
    }
}
