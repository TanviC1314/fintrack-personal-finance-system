package com.fintrack.budget.service;

import com.fintrack.budget.dto.BudgetResponse;
import com.fintrack.budget.dto.CreateBudgetRequest;
import com.fintrack.budget.entity.Budget;
import com.fintrack.budget.exception.BudgetAlreadyExistsException;
import com.fintrack.budget.repository.BudgetRepository;
import com.fintrack.category.entity.Category;
import com.fintrack.category.service.CategoryService;
import com.fintrack.common.exception.InvalidRequestException;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.transaction.repository.CategoryTotal;
import com.fintrack.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monthly category budgets and how much of each has been used. Spending is never stored: it is
 * calculated from the ledger by the database every time, so it cannot drift out of date.
 */
@Service
public class BudgetService {

    /** Budgets may be set up this many months ahead of the current month. */
    static final int MAX_MONTHS_AHEAD = 24;

    private final BudgetRepository budgetRepository;
    private final CategoryService categoryService;
    private final TransactionRepository transactionRepository;
    private final Clock clock;

    public BudgetService(BudgetRepository budgetRepository, CategoryService categoryService,
                         TransactionRepository transactionRepository, Clock clock) {
        this.budgetRepository = budgetRepository;
        this.categoryService = categoryService;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    @Transactional
    public BudgetResponse create(UUID userId, CreateBudgetRequest request) {
        requireReasonableMonth(request.month());
        Category category = categoryService.getOwned(userId, request.categoryId());
        BigDecimal amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);

        Budget budget = new Budget(userId, category, request.currency(), request.month().atDay(1), amount);
        try {
            budget = budgetRepository.saveAndFlush(budget);
        } catch (DataIntegrityViolationException e) {
            // Two requests for the same category, month and currency can both pass any pre-check;
            // the unique constraint is the real guard.
            throw new BudgetAlreadyExistsException();
        }
        return withUtilisation(userId, List.of(budget)).get(0);
    }

    @Transactional
    public BudgetResponse updateAmount(UUID userId, UUID budgetId, BigDecimal newAmount) {
        Budget budget = budgetRepository.findByIdAndUserId(budgetId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Budget not found"));
        budget.changeAmount(newAmount.setScale(2, RoundingMode.UNNECESSARY));
        budgetRepository.saveAndFlush(budget);
        return withUtilisation(userId, List.of(budget)).get(0);
    }

    /**
     * The caller's budgets for one month (default: the current month) with their utilisation, ordered by
     * category name. Optionally only one currency and/or only the budgets that have been exceeded.
     */
    @Transactional(readOnly = true)
    public List<BudgetResponse> list(UUID userId, YearMonth month, String currency, boolean exceededOnly) {
        YearMonth period = month != null ? month : YearMonth.now(clock);
        List<Budget> budgets = budgetRepository
                .findAllByUserIdAndMonthOrderByCategoryNameAscCurrencyAsc(userId, period.atDay(1)).stream()
                .filter(b -> currency == null || currency.isBlank() || b.getCurrency().equalsIgnoreCase(currency.strip()))
                .toList();
        List<BudgetResponse> responses = withUtilisation(userId, budgets);
        return exceededOnly ? responses.stream().filter(BudgetResponse::exceeded).toList() : responses;
    }

    /** One aggregate query per currency present, not one per budget. */
    private List<BudgetResponse> withUtilisation(UUID userId, List<Budget> budgets) {
        Map<String, Map<UUID, BigDecimal>> spentByCurrency = new HashMap<>();
        for (Budget budget : budgets) {
            spentByCurrency.computeIfAbsent(budget.getCurrency(), currency -> {
                YearMonth month = YearMonth.from(budget.getMonth());
                Map<UUID, BigDecimal> spent = new HashMap<>();
                for (CategoryTotal row : transactionRepository.expenseTotalsByCategory(
                        userId, currency, month.atDay(1), month.atEndOfMonth())) {
                    if (row.categoryId() != null) {
                        spent.put(row.categoryId(), row.total());
                    }
                }
                return spent;
            });
        }
        return budgets.stream()
                .map(b -> BudgetResponse.of(b, spentByCurrency.get(b.getCurrency())
                        .getOrDefault(b.getCategory().getId(), BigDecimal.ZERO.setScale(2))))
                .toList();
    }

    private void requireReasonableMonth(YearMonth month) {
        if (month.isAfter(YearMonth.now(clock).plusMonths(MAX_MONTHS_AHEAD))) {
            throw new InvalidRequestException(
                    "Budgets can be set at most " + MAX_MONTHS_AHEAD + " months ahead");
        }
    }
}
