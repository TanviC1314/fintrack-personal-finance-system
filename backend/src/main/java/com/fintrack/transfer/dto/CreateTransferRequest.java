package com.fintrack.transfer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * @param transferDate optional; defaults to today
 */
public record CreateTransferRequest(

        @NotNull(message = "Source account is required")
        UUID sourceAccountId,

        @NotNull(message = "Destination account is required")
        UUID destinationAccountId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        @Digits(integer = 17, fraction = 2, message = "Amount must have at most 17 digits and 2 decimal places")
        BigDecimal amount,

        @Size(max = 255, message = "Description must be at most 255 characters")
        String description,

        LocalDate transferDate) {
}
