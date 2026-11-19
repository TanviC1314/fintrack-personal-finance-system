package com.fintrack.transaction.service;

import com.fintrack.account.entity.Account;
import com.fintrack.account.exception.AccountArchivedException;
import com.fintrack.account.service.AccountService;
import com.fintrack.audit.entity.AuditAction;
import com.fintrack.audit.entity.AuditEntityType;
import com.fintrack.audit.service.AuditService;
import com.fintrack.category.entity.Category;
import com.fintrack.category.service.CategoryService;
import com.fintrack.common.exception.InvalidRequestException;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.common.validation.DateRules;
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
import java.util.Map;
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
    private final CategoryService categoryService;
    private final AuditService auditService;
    private final Clock clock;

    public TransactionService(TransactionRepository transactionRepository, AccountService accountService,
                              CategoryService categoryService, AuditService auditService, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.accountService = accountService;
        this.categoryService = categoryService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public Transaction create(UUID userId, CreateTransactionRequest request) {
        if (request.type().isTransfer()) {
            throw new InvalidRequestException("Use the transfer endpoint to move money between accounts");
        }
        validateDate(request.transactionDate());
        BigDecimal amount = normalise(request.amount());

        Category category = categoryOrNull(userId, request.categoryId());
        Account account = accountService.getForUpdate(userId, request.accountId());
        requireActive(account);
        applyEffect(account, request.type(), amount);

        Transaction saved = transactionRepository.save(new Transaction(
                account, request.type(), amount, category,
                blankToNull(request.description()), request.transactionDate()));
        auditService.record(userId, AuditEntityType.TRANSACTION, saved.getId(), AuditAction.CREATED, Map.of(
                "accountId", account.getId().toString(), "type", request.type().name(), "amount", amount));
        return saved;
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
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sortFor(pageable.getSort()));
        return transactionRepository.findAll(TransactionSpecifications.matching(userId, filter), sorted);
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
        Category category = categoryOrNull(userId, request.categoryId());

        Account account = lockAccountOf(userId, transactionId);
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        requireChangeable(transaction, account);

        BigDecimal previousAmount = transaction.getAmount();
        BigDecimal difference = newAmount.subtract(previousAmount);
        if (difference.signum() != 0) {
            // A larger amount moves more money in the transaction's direction; a smaller one gives some back.
            boolean movesMoreInOriginalDirection = difference.signum() > 0;
            boolean credit = transaction.getType().isCredit() == movesMoreInOriginalDirection;
            applyDirection(account, credit, difference.abs());
        }
        transaction.update(newAmount, category, blankToNull(request.description()), request.transactionDate());
        auditService.record(userId, AuditEntityType.TRANSACTION, transactionId, AuditAction.UPDATED,
                Map.of("previousAmount", previousAmount, "newAmount", newAmount));
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
        auditService.record(userId, AuditEntityType.TRANSACTION, transactionId, AuditAction.REVERSED, Map.of(
                "accountId", account.getId().toString(), "type", transaction.getType().name(),
                "amount", transaction.getAmount()));
    }

    /** The category must belong to the caller; someone else's category is reported as not found. */
    private Category categoryOrNull(UUID userId, UUID categoryId) {
        return categoryId == null ? null : categoryService.getOwned(userId, categoryId);
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

    private void validateDate(LocalDate date) {
        DateRules.requireNotInFuture(date, clock);
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
