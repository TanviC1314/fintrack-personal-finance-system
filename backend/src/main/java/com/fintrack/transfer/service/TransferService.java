package com.fintrack.transfer.service;

import com.fintrack.account.entity.Account;
import com.fintrack.account.exception.AccountArchivedException;
import com.fintrack.account.service.AccountService;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.InvalidRequestException;
import com.fintrack.common.validation.DateRules;
import com.fintrack.transaction.entity.Transaction;
import com.fintrack.transaction.entity.TransactionType;
import com.fintrack.transaction.repository.TransactionRepository;
import com.fintrack.transfer.dto.CreateTransferRequest;
import com.fintrack.transfer.entity.Transfer;
import com.fintrack.transfer.repository.TransferRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves money between two of the caller's own accounts. Everything happens in one database
 * transaction: both balances and the three new rows (the transfer and its two ledger legs) are
 * committed together or not at all. Any failure rolls the whole thing back, so a transfer can never
 * leave money debited but not credited.
 */
@Service
public class TransferService {

    private final TransferRepository transferRepository;
    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final Clock clock;

    public TransferService(TransferRepository transferRepository, TransactionRepository transactionRepository,
                           AccountService accountService, Clock clock) {
        this.transferRepository = transferRepository;
        this.transactionRepository = transactionRepository;
        this.accountService = accountService;
        this.clock = clock;
    }

    @Transactional
    public Transfer transfer(UUID userId, CreateTransferRequest request, String idempotencyKey) {
        UUID sourceId = request.sourceAccountId();
        UUID destinationId = request.destinationAccountId();
        if (sourceId.equals(destinationId)) {
            throw new InvalidRequestException("Source and destination accounts must be different");
        }
        LocalDate date = request.transferDate() == null ? LocalDate.now(clock) : request.transferDate();
        DateRules.requireNotInFuture(date, clock);
        BigDecimal amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);

        Map<UUID, Account> locked = lockInFixedOrder(userId, sourceId, destinationId);
        Account source = locked.get(sourceId);
        Account destination = locked.get(destinationId);
        requireTransferable(source, destination);

        source.debit(amount);
        destination.credit(amount);

        String description = blankToNull(request.description());
        Transfer transfer = transferRepository.saveAndFlush(new Transfer(
                userId, sourceId, destinationId, amount, description, date, idempotencyKey));
        transactionRepository.save(Transaction.transferLeg(source, TransactionType.TRANSFER_OUT, amount,
                legDescription(description, "Transfer to " + destination.getName()), date, transfer.getId()));
        transactionRepository.save(Transaction.transferLeg(destination, TransactionType.TRANSFER_IN, amount,
                legDescription(description, "Transfer from " + source.getName()), date, transfer.getId()));
        return transfer;
    }

    /**
     * Locks both accounts in ascending id order regardless of transfer direction. If A-to-B and B-to-A
     * ran at the same time and each locked its own source first, they would wait on each other forever;
     * a single global order makes that cycle impossible.
     */
    private Map<UUID, Account> lockInFixedOrder(UUID userId, UUID first, UUID second) {
        Map<UUID, Account> locked = new HashMap<>();
        for (UUID id : Stream.of(first, second).sorted().toList()) {
            locked.put(id, accountService.getForUpdate(userId, id));
        }
        return locked;
    }

    private static void requireTransferable(Account source, Account destination) {
        if (!source.isActive() || !destination.isActive()) {
            throw new AccountArchivedException();
        }
        if (!source.getCurrency().equals(destination.getCurrency())) {
            throw new BusinessRuleException("Transfers between accounts with different currencies are not supported");
        }
    }

    private static String legDescription(String userDescription, String fallback) {
        return userDescription != null ? userDescription : fallback;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
