package com.fintrack.transfer.service;

import com.fintrack.account.entity.Account;
import com.fintrack.account.exception.AccountArchivedException;
import com.fintrack.account.service.AccountService;
import com.fintrack.audit.entity.AuditAction;
import com.fintrack.audit.entity.AuditEntityType;
import com.fintrack.audit.service.AuditService;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.InvalidRequestException;
import com.fintrack.common.validation.DateRules;
import com.fintrack.transaction.entity.Transaction;
import com.fintrack.transaction.entity.TransactionType;
import com.fintrack.transaction.repository.TransactionRepository;
import com.fintrack.transfer.dto.CreateTransferRequest;
import com.fintrack.transfer.entity.Transfer;
import com.fintrack.transfer.exception.IdempotencyKeyReusedException;
import com.fintrack.transfer.repository.TransferRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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

    private static final int MAX_KEY_LENGTH = 100;
    private static final Pattern PRINTABLE_ASCII = Pattern.compile("[\\x21-\\x7E]+");
    private static final String IDEMPOTENCY_CONSTRAINT = "uq_transfers_user_idempotency_key";

    private final TransferRepository transferRepository;
    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final AuditService auditService;
    private final Clock clock;

    public TransferService(TransferRepository transferRepository, TransactionRepository transactionRepository,
                           AccountService accountService, AuditService auditService, Clock clock) {
        this.transferRepository = transferRepository;
        this.transactionRepository = transactionRepository;
        this.accountService = accountService;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * Performs the transfer, or, if a transfer with the same idempotency key already exists for this
     * user, returns that one without moving any money. A client that times out can therefore retry
     * safely with the same key.
     *
     * <p>The key is checked twice. The first check is a cheap fast path. The second runs after the
     * account locks are held: two identical concurrent requests lock the same accounts, so the second
     * one waits until the first commits and then sees its transfer. The unique constraint on
     * (user_id, idempotency_key) is the final guard.
     */
    @Transactional
    public TransferResult transfer(UUID userId, CreateTransferRequest request, String idempotencyKey) {
        requireValidKey(idempotencyKey);
        UUID sourceId = request.sourceAccountId();
        UUID destinationId = request.destinationAccountId();
        BigDecimal amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);

        Optional<Transfer> earlier = transferRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (earlier.isPresent()) {
            return replay(earlier.get(), sourceId, destinationId, amount);
        }

        if (sourceId.equals(destinationId)) {
            throw new InvalidRequestException("Source and destination accounts must be different");
        }
        LocalDate date = request.transferDate() == null ? LocalDate.now(clock) : request.transferDate();
        DateRules.requireNotInFuture(date, clock);

        Map<UUID, Account> locked = lockInFixedOrder(userId, sourceId, destinationId);
        Account source = locked.get(sourceId);
        Account destination = locked.get(destinationId);

        earlier = transferRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (earlier.isPresent()) {
            return replay(earlier.get(), sourceId, destinationId, amount);
        }
        requireTransferable(source, destination);

        source.debit(amount);
        destination.credit(amount);

        String description = blankToNull(request.description());
        Transfer transfer = saveTransfer(
                new Transfer(userId, sourceId, destinationId, amount, description, date, idempotencyKey));
        transactionRepository.save(Transaction.transferLeg(source, TransactionType.TRANSFER_OUT, amount,
                legDescription(description, "Transfer to " + destination.getName()), date, transfer.getId()));
        transactionRepository.save(Transaction.transferLeg(destination, TransactionType.TRANSFER_IN, amount,
                legDescription(description, "Transfer from " + source.getName()), date, transfer.getId()));
        auditService.record(userId, AuditEntityType.TRANSFER, transfer.getId(), AuditAction.CREATED, Map.of(
                "sourceAccountId", sourceId.toString(), "destinationAccountId", destinationId.toString(),
                "amount", amount));
        return new TransferResult(transfer, false);
    }

    /**
     * The caller's transfers, newest first, optionally only those into or out of one account. The order
     * is fixed (a client-supplied sort is ignored) and ends in the id so pages never repeat or skip rows.
     */
    @Transactional(readOnly = true)
    public Page<Transfer> list(UUID userId, UUID accountId, Pageable pageable) {
        Specification<Transfer> spec = (root, query, cb) -> cb.equal(root.get("userId"), userId);
        if (accountId != null) {
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.equal(root.get("sourceAccountId"), accountId),
                    cb.equal(root.get("destinationAccountId"), accountId)));
        }
        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return transferRepository.findAll(spec, newestFirst);
    }

    /** The same key with the same accounts and amount is a retry; anything else is a client mistake. */
    private static TransferResult replay(Transfer earlier, UUID sourceId, UUID destinationId, BigDecimal amount) {
        boolean sameRequest = earlier.getSourceAccountId().equals(sourceId)
                && earlier.getDestinationAccountId().equals(destinationId)
                && earlier.getAmount().compareTo(amount) == 0;
        if (!sameRequest) {
            throw new IdempotencyKeyReusedException();
        }
        return new TransferResult(earlier, true);
    }

    private Transfer saveTransfer(Transfer transfer) {
        try {
            return transferRepository.saveAndFlush(transfer);
        } catch (DataIntegrityViolationException e) {
            // A request with the same key but different accounts ran at the same moment and won.
            // Only the idempotency constraint means that; any other violation is a real bug.
            Throwable cause = e.getMostSpecificCause();
            if (cause.getMessage() != null && cause.getMessage().contains(IDEMPOTENCY_CONSTRAINT)) {
                throw new IdempotencyKeyReusedException();
            }
            throw e;
        }
    }

    private static void requireValidKey(String key) {
        if (key == null || key.isEmpty() || key.length() > MAX_KEY_LENGTH || !PRINTABLE_ASCII.matcher(key).matches()) {
            throw new InvalidRequestException(
                    "Idempotency-Key must be 1 to 100 printable characters without spaces");
        }
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
