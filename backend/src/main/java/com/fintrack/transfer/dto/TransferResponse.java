package com.fintrack.transfer.dto;

import com.fintrack.transfer.entity.Transfer;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        String description,
        LocalDate transferDate,
        Instant createdAt) {

    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getSourceAccountId(),
                transfer.getDestinationAccountId(),
                transfer.getAmount(),
                transfer.getDescription(),
                transfer.getTransferDate(),
                transfer.getCreatedAt());
    }
}
