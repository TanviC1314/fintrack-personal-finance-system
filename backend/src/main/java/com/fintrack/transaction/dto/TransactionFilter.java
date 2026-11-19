package com.fintrack.transaction.dto;

import com.fintrack.transaction.entity.TransactionType;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Optional search criteria; a null field means "do not filter on this".
 *
 * @param from           earliest transaction date, inclusive
 * @param to             latest transaction date, inclusive
 * @param includeReversed reversed transactions are hidden unless this is true
 */
public record TransactionFilter(
        UUID accountId,
        TransactionType type,
        String category,
        LocalDate from,
        LocalDate to,
        boolean includeReversed) {
}
