package com.fintrack.transaction.service;

import com.fintrack.account.entity.Account;
import com.fintrack.account.exception.AccountArchivedException;
import com.fintrack.account.service.AccountService;
import com.fintrack.common.exception.InvalidRequestException;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.transaction.dto.CreateTransactionRequest;
import com.fintrack.transaction.dto.TransactionFilter;
import com.fintrack.transaction.dto.UpdateTransactionRequest;
import com.fintrack.transaction.entity.Transaction;
import com.fintrack.transaction.entity.TransactionType;
import com.fintrack.transaction.exception.TransactionNotEditableException;
import com.fintrack.transaction.repository.TransactionRepository;
import com.fintrack.transaction.repository.TransactionSpecifications;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Income and expense transactions. Each operation changes the ledger row and the account's cached
 * balance in one database transaction while holding the account's row lock, so the two always agree
 * and concurrent operations on the same account run one after another.
 */
@Service
public class TransactionService {

    private static final List<String> SORTABLE_PROPERTIES = List.of("transactionDate", "amount", "createdAt");

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final Clock clock;

    public TransactionService(TransactionRepository transactionRepository, AccountService accountService, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.accountService = accountService;
        this.clock = clock;
    }

    @Transactional
    public Transaction create(UUID userId, CreateTransactionRequest request) {
        if (request.type().isTransfer()) {
            throw new InvalidRequestException("Use the transfer endpoint to move money between accounts");
        }
        validateDate(request.transactionDate());
        BigDecimal amount = normalise(request.amount());

        Account account = accountService.getForUpdate(userId, request.accountId());
        requireActive(account);
        applyEffect(account, request.type(), amount);

        return transactionRepository.save(new Transaction(
                account, request.type(), amount,
                blankToNull(request.category()), blankToNull(request.description()), request.transactionDate()));
    }

    @Transactional(readOnly = true)
    public Transaction get(UUID userId, UUID transactionId) {
        return transactionRepository.findByIdAndAccountUserId(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
    }

    /**
     * Searches the caller's transactions with optional filters, newest first by default. Only the
     * whitelisted sort properties are accepted so clients cannot sort by (or probe) arbitrary columns.
     */
    @Transactional(readOnly = true)
    public Page<Transaction> search(UUID userId, TransactionFilter filter, Pageable pageable) {
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new InvalidRequestException("'from' must not be after 'to'");
        }
        TransactionFilter cleaned = new TransactionFilter(filter.accountId(), filter.type(),
                blankToNull(filter.category()), filter.from(), filter.to(), filter.includeReversed());

        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sortFor(pageable.getSort()));
        return transactionRepository.findAll(TransactionSpecifications.matching(userId, cleaned), sorted);
    }

    private static Sort sortFor(Sort requested) {
        List<Sort.Order> orders = new ArrayList<>();
        for (Sort.Order order : requested) {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new InvalidRequestException("Cannot sort by '" + order.getProperty()
                        + "'. Allowed: " + String.join(", ", SORTABLE_PROPERTIES));
            }
            orders.add(order);
        }
        if (orders.isEmpty()) {
            orders.add(Sort.Order.desc("transactionDate"));
        }
        // Tie-breakers make the order total, so pages never repeat or skip rows.
        addIfAbsent(orders, Sort.Order.desc("createdAt"));
        addIfAbsent(orders, Sort.Order.desc("id"));
        return Sort.by(orders);
    }

    private static void addIfAbsent(List<Sort.Order> orders, Sort.Order candidate) {
        if (orders.stream().noneMatch(o -> o.getProperty().equals(candidate.getProperty()))) {
            orders.add(candidate);
        }
    }

    @Transactional
    public Transaction update(UUID userId, UUID transactionId, UpdateTransactionRequest request) {
        validateDate(request.transactionDate());
        BigDecimal newAmount = normalise(request.amount());

        Account account = lockAccountOf(userId, transactionId);
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        requireChangeable(transaction, account);

        BigDecimal difference = newAmount.subtract(transaction.getAmount());
        if (difference.signum() != 0) {
            // A larger amount moves more money in the transaction's direction; a smaller one gives some back.
            boolean movesMoreInOriginalDirection = difference.signum() > 0;
            boolean credit = transaction.getType().isCredit() == movesMoreInOriginalDirection;
            applyDirection(account, credit, difference.abs());
        }
        transaction.update(newAmount, blankToNull(request.category()), blankToNull(request.description()),
                request.transactionDate());
        return transaction;
    }

    /**
     * Reverses a transaction: the opposite balance change is applied and the row is marked reversed
     * rather than deleted, so history is kept. Reversing an already reversed transaction does nothing.
     */
    @Transactional
    public void reverse(UUID userId, UUID transactionId) {
        Account account = lockAccountOf(userId, transactionId);
        // Loaded only after the lock is held, so a concurrent reversal that just committed is seen.
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        if (transaction.isReversed()) {
            return;
        }
        requireChangeable(transaction, account);

        applyDirection(account, !transaction.getType().isCredit(), transaction.getAmount());
        transaction.markReversed(clock.instant());
    }

    private Account lockAccountOf(UUID userId, UUID transactionId) {
        UUID accountId = transactionRepository.findAccountIdByIdAndOwner(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        return accountService.getForUpdate(userId, accountId);
    }

    private void requireChangeable(Transaction transaction, Account account) {
        if (transaction.isReversed()) {
            throw TransactionNotEditableException.reversed();
        }
        if (transaction.getType().isTransfer()) {
            throw TransactionNotEditableException.transferLeg();
        }
        requireActive(account);
    }

    private static void requireActive(Account account) {
        if (!account.isActive()) {
            throw new AccountArchivedException();
        }
    }

    private static void applyEffect(Account account, TransactionType type, BigDecimal amount) {
        applyDirection(account, type.isCredit(), amount);
    }

    private static void applyDirection(Account account, boolean credit, BigDecimal amount) {
        if (credit) {
            account.credit(amount);
        } else {
            account.debit(amount);
        }
    }

    /** Allows one day ahead of UTC so a user in a timezone ahead of UTC can record "today". */
    private void validateDate(LocalDate date) {
        if (date.isAfter(LocalDate.now(clock).plusDays(1))) {
            throw new InvalidRequestException("Transaction date cannot be in the future");
        }
    }

    private static BigDecimal normalise(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
