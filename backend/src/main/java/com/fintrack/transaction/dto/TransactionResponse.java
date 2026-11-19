package com.fintrack.transaction.dto;

import com.fintrack.transaction.entity.Transaction;
import com.fintrack.transaction.entity.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID accountId,
        TransactionType type,
        BigDecimal amount,
        String category,
        String description,
        LocalDate transactionDate,
        Instant reversedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAccountId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getCategory(),
                transaction.getDescription(),
                transaction.getTransactionDate(),
                transaction.getReversedAt(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt());
    }
}
