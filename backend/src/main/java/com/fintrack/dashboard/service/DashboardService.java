package com.fintrack.dashboard.service;

import com.fintrack.account.entity.Account;
import com.fintrack.account.entity.AccountStatus;
import com.fintrack.account.repository.AccountRepository;
import com.fintrack.common.exception.InvalidRequestException;
import com.fintrack.dashboard.dto.CategorySpendingReport;
import com.fintrack.dashboard.dto.DashboardSummary;
import com.fintrack.dashboard.dto.MonthlyTrend;
import com.fintrack.transaction.dto.TransactionResponse;
import com.fintrack.transaction.entity.TransactionType;
import com.fintrack.transaction.repository.CategoryTotal;
import com.fintrack.transaction.repository.MonthlyTypeTotal;
import com.fintrack.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only financial summaries. The numbers come from SQL aggregate queries; this class only chooses
 * the month and currency, fills gaps and does the final arithmetic. Everything is for one currency at
 * a time because amounts in different currencies cannot be added together.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    static final int RECENT_TRANSACTIONS = 10;
    static final int DEFAULT_TREND_MONTHS = 6;
    static final int MAX_TREND_MONTHS = 24;
    static final String UNCATEGORISED = "Uncategorised";

    private static final Pattern CURRENCY = Pattern.compile("[A-Za-z]{3}");

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final Clock clock;

    public DashboardService(TransactionRepository transactionRepository, AccountRepository accountRepository,
                            Clock clock) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.clock = clock;
    }

    public DashboardSummary summary(UUID userId, YearMonth month, String requestedCurrency) {
        YearMonth period = monthOrCurrent(month);
        String currency = resolveCurrency(userId, requestedCurrency);
        if (currency == null) {
            return new DashboardSummary(null, period.toString(), zero(), zero(), zero(), zero(), List.of());
        }

        BigDecimal income = total(userId, currency, TransactionType.INCOME, period);
        BigDecimal expenses = total(userId, currency, TransactionType.EXPENSE, period);
        List<TransactionResponse> recent = transactionRepository
                .findRecent(userId, currency, PageRequest.of(0, RECENT_TRANSACTIONS))
                .stream().map(TransactionResponse::from).toList();
        return new DashboardSummary(currency, period.toString(),
                accountRepository.sumActiveBalance(userId, currency),
                income, expenses, income.subtract(expenses), recent);
    }

    /** Income against expenses for {@code months} consecutive months ending at {@code lastMonth}. */
    public MonthlyTrend monthlyTrend(UUID userId, Integer months, YearMonth lastMonth, String requestedCurrency) {
        int count = months == null ? DEFAULT_TREND_MONTHS : months;
        if (count < 1 || count > MAX_TREND_MONTHS) {
            throw new InvalidRequestException("months must be between 1 and " + MAX_TREND_MONTHS);
        }
        YearMonth last = monthOrCurrent(lastMonth);
        YearMonth first = last.minusMonths(count - 1L);
        String currency = resolveCurrency(userId, requestedCurrency);

        Map<YearMonth, BigDecimal> income = new HashMap<>();
        Map<YearMonth, BigDecimal> expenses = new HashMap<>();
        if (currency != null) {
            List<MonthlyTypeTotal> rows = transactionRepository.monthlyTotals(userId, currency,
                    List.of(TransactionType.INCOME, TransactionType.EXPENSE), first.atDay(1), last.atEndOfMonth());
            for (MonthlyTypeTotal row : rows) {
                YearMonth month = YearMonth.of(row.year(), row.month());
                (row.type() == TransactionType.INCOME ? income : expenses).put(month, row.total());
            }
        }

        List<MonthlyTrend.Point> points = new ArrayList<>();
        for (YearMonth month = first; !month.isAfter(last); month = month.plusMonths(1)) {
            BigDecimal in = income.getOrDefault(month, zero());
            BigDecimal out = expenses.getOrDefault(month, zero());
            points.add(new MonthlyTrend.Point(month.toString(), in, out, in.subtract(out)));
        }
        return new MonthlyTrend(currency, points);
    }

    public CategorySpendingReport spendingByCategory(UUID userId, YearMonth month, String requestedCurrency) {
        YearMonth period = monthOrCurrent(month);
        String currency = resolveCurrency(userId, requestedCurrency);
        if (currency == null) {
            return new CategorySpendingReport(null, period.toString(), zero(), List.of());
        }

        List<CategoryTotal> rows = transactionRepository.expenseTotalsByCategory(
                userId, currency, period.atDay(1), period.atEndOfMonth());
        BigDecimal totalSpent = rows.stream().map(CategoryTotal::total).reduce(zero(), BigDecimal::add);
        List<CategorySpendingReport.Entry> entries = rows.stream()
                .map(row -> new CategorySpendingReport.Entry(
                        row.categoryId(),
                        row.categoryName() == null ? UNCATEGORISED : row.categoryName(),
                        row.total(),
                        percentage(row.total(), totalSpent)))
                .toList();
        return new CategorySpendingReport(currency, period.toString(), totalSpent, entries);
    }

    /**
     * The requested currency (upper-cased), otherwise the currency of the caller's oldest active account.
     * Null means the caller has no accounts, so there is nothing to summarise.
     */
    private String resolveCurrency(UUID userId, String requested) {
        if (requested != null && !requested.isBlank()) {
            if (!CURRENCY.matcher(requested.strip()).matches()) {
                throw new InvalidRequestException("currency must be a three-letter code such as EUR");
            }
            return requested.strip().toUpperCase(Locale.ROOT);
        }
        return accountRepository.findFirstByUserIdAndStatusOrderByCreatedAtAsc(userId, AccountStatus.ACTIVE)
                .map(Account::getCurrency)
                .orElse(null);
    }

    private YearMonth monthOrCurrent(YearMonth month) {
        return month != null ? month : YearMonth.now(clock);
    }

    private BigDecimal total(UUID userId, String currency, TransactionType type, YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        return transactionRepository.sumAmount(userId, currency, type, from, to);
    }

    private static BigDecimal percentage(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2);
    }
}
