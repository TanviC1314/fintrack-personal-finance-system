package com.fintrack.account.dto;

import com.fintrack.account.entity.Account;
import com.fintrack.account.entity.AccountStatus;
import com.fintrack.account.entity.AccountType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String name,
        AccountType type,
        String currency,
        BigDecimal openingBalance,
        BigDecimal currentBalance,
        AccountStatus status,
        Instant createdAt) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getType(),
                account.getCurrency(),
                account.getOpeningBalance(),
                account.getCurrentBalance(),
                account.getStatus(),
                account.getCreatedAt());
    }
}
