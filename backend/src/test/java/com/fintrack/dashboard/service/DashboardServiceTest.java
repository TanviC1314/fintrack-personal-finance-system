package com.fintrack.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fintrack.account.entity.Account;
import com.fintrack.account.entity.AccountStatus;
import com.fintrack.account.entity.AccountType;
import com.fintrack.account.repository.AccountRepository;
import com.fintrack.common.exception.InvalidRequestException;
import com.fintrack.dashboard.dto.CategorySpendingReport;
import com.fintrack.dashboard.dto.DashboardSummary;
import com.fintrack.dashboard.dto.MonthlyTrend;
import com.fintrack.transaction.entity.TransactionType;
import com.fintrack.transaction.repository.CategoryTotal;
import com.fintrack.transaction.repository.MonthlyTypeTotal;
import com.fintrack.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final YearMonth MARCH = YearMonth.of(2026, 3);

    private final UUID userId = UUID.randomUUID();

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AccountRepository accountRepository;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        // "Now" is 15 March 2026.
        service = new DashboardService(transactionRepository, accountRepository,
                Clock.fixed(Instant.parse("2026-03-15T12:00:00Z"), ZoneOffset.UTC));
    }

    private void callerHasAnActiveAccountIn(String currency) {
        Account account = new Account(userId, "Main", AccountType.CURRENT, currency, BigDecimal.ZERO);
        when(accountRepository.findFirstByUserIdAndStatusOrderByCreatedAtAsc(userId, AccountStatus.ACTIVE))
                .thenReturn(Optional.of(account));
    }

    private void totals(String currency, YearMonth month, String income, String expenses) {
        when(transactionRepository.sumAmount(userId, currency, TransactionType.INCOME, month.atDay(1), month.atEndOfMonth()))
                .thenReturn(new BigDecimal(income));
        when(transactionRepository.sumAmount(userId, currency, TransactionType.EXPENSE, month.atDay(1), month.atEndOfMonth()))
                .thenReturn(new BigDecimal(expenses));
    }

    // ---- summary ----

    @Test
    void summaryDefaultsToTheCurrentMonthAndTheOldestAccountsCurrency() {
        callerHasAnActiveAccountIn("EUR");
        totals("EUR", MARCH, "3000.00", "260.00");
        when(accountRepository.sumActiveBalance(userId, "EUR")).thenReturn(new BigDecimal("3800.00"));
        when(transactionRepository.findRecent(userId, "EUR", PageRequest.of(0, 10))).thenReturn(List.of());

        DashboardSummary summary = service.summary(userId, null, null);

        assertThat(summary.currency()).isEqualTo("EUR");
        assertThat(summary.month()).isEqualTo("2026-03");
        assertThat(summary.totalBalance()).isEqualByComparingTo("3800.00");
        assertThat(summary.monthlyIncome()).isEqualByComparingTo("3000.00");
        assertThat(summary.monthlyExpenses()).isEqualByComparingTo("260.00");
        assertThat(summary.monthlySavings()).isEqualByComparingTo("2740.00");
    }

    @Test
    void savingsAreNegativeWhenMoreWasSpentThanEarned() {
        callerHasAnActiveAccountIn("EUR");
        totals("EUR", MARCH, "100.00", "250.25");
        when(accountRepository.sumActiveBalance(userId, "EUR")).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.findRecent(any(), any(), any())).thenReturn(List.of());

        assertThat(service.summary(userId, MARCH, null).monthlySavings()).isEqualByComparingTo("-150.25");
    }

    @Test
    void summaryUsesTheRequestedMonthAndUpperCasesTheCurrency() {
        YearMonth january = YearMonth.of(2025, 1);
        totals("USD", january, "5.00", "2.00");
        when(accountRepository.sumActiveBalance(userId, "USD")).thenReturn(new BigDecimal("3.00"));
        when(transactionRepository.findRecent(userId, "USD", PageRequest.of(0, 10))).thenReturn(List.of());

        DashboardSummary summary = service.summary(userId, january, " usd ");

        assertThat(summary.currency()).isEqualTo("USD");
        assertThat(summary.month()).isEqualTo("2025-01");
        verify(accountRepository, never()).findFirstByUserIdAndStatusOrderByCreatedAtAsc(any(), any());
    }

    @Test
    void aCallerWithNoAccountsGetsZerosWithoutAnyQueries() {
        when(accountRepository.findFirstByUserIdAndStatusOrderByCreatedAtAsc(userId, AccountStatus.ACTIVE))
                .thenReturn(Optional.empty());

        DashboardSummary summary = service.summary(userId, MARCH, null);

        assertThat(summary.currency()).isNull();
        assertThat(summary.totalBalance()).isEqualByComparingTo("0");
        assertThat(summary.monthlySavings()).isEqualByComparingTo("0");
        assertThat(summary.recentTransactions()).isEmpty();
        verifyNoInteractions(transactionRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EURO", "E1", "€", "EU"})
    void malformedCurrenciesAreRejected(String currency) {
        assertThatThrownBy(() -> service.summary(userId, MARCH, currency)).isInstanceOf(InvalidRequestException.class);
    }

    // ---- monthly trend ----

    @Test
    void trendCoversTheLastSixMonthsOldestFirstAndFillsEmptyMonthsWithZero() {
        callerHasAnActiveAccountIn("EUR");
        when(transactionRepository.monthlyTotals(eq(userId), eq("EUR"),
                eq(List.of(TransactionType.INCOME, TransactionType.EXPENSE)),
                eq(LocalDate.of(2025, 10, 1)), eq(LocalDate.of(2026, 3, 31))))
                .thenReturn(List.of(
                        new MonthlyTypeTotal(2025, 12, TransactionType.INCOME, new BigDecimal("500.00")),
                        new MonthlyTypeTotal(2025, 12, TransactionType.EXPENSE, new BigDecimal("120.00")),
                        new MonthlyTypeTotal(2026, 3, TransactionType.EXPENSE, new BigDecimal("40.00"))));

        MonthlyTrend trend = service.monthlyTrend(userId, null, null, null);

        assertThat(trend.currency()).isEqualTo("EUR");
        assertThat(trend.months()).extracting(MonthlyTrend.Point::month)
                .containsExactly("2025-10", "2025-11", "2025-12", "2026-01", "2026-02", "2026-03");
        MonthlyTrend.Point december = trend.months().get(2);
        assertThat(december.income()).isEqualByComparingTo("500.00");
        assertThat(december.expenses()).isEqualByComparingTo("120.00");
        assertThat(december.savings()).isEqualByComparingTo("380.00");
        MonthlyTrend.Point january = trend.months().get(3);
        assertThat(january.income()).isEqualByComparingTo("0");
        assertThat(january.savings()).isEqualByComparingTo("0");
        assertThat(trend.months().get(5).savings()).isEqualByComparingTo("-40.00"); // expenses only
    }

    @Test
    void trendCrossesYearBoundariesCorrectly() {
        when(transactionRepository.monthlyTotals(eq(userId), eq("EUR"), any(),
                eq(LocalDate.of(2024, 11, 1)), eq(LocalDate.of(2025, 2, 28)))).thenReturn(List.of());

        MonthlyTrend trend = service.monthlyTrend(userId, 4, YearMonth.of(2025, 2), "EUR");

        assertThat(trend.months()).extracting(MonthlyTrend.Point::month)
                .containsExactly("2024-11", "2024-12", "2025-01", "2025-02");
    }

    @Test
    void trendLengthIsLimited() {
        callerHasAnActiveAccountIn("EUR");
        when(transactionRepository.monthlyTotals(any(), any(), any(), any(), any())).thenReturn(List.of());

        assertThat(service.monthlyTrend(userId, 1, null, null).months()).hasSize(1);
        assertThat(service.monthlyTrend(userId, 24, null, null).months()).hasSize(24);
        assertThatThrownBy(() -> service.monthlyTrend(userId, 0, null, null)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.monthlyTrend(userId, 25, null, null)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void trendForACallerWithoutAccountsIsAllZeros() {
        when(accountRepository.findFirstByUserIdAndStatusOrderByCreatedAtAsc(userId, AccountStatus.ACTIVE))
                .thenReturn(Optional.empty());

        MonthlyTrend trend = service.monthlyTrend(userId, 3, null, null);

        assertThat(trend.currency()).isNull();
        assertThat(trend.months()).hasSize(3).allMatch(p -> p.income().signum() == 0 && p.expenses().signum() == 0);
        verifyNoInteractions(transactionRepository);
    }

    // ---- spending by category ----

    @Test
    void categoriesCarryTheirShareOfTotalSpendingToOneDecimal() {
        UUID foodId = UUID.randomUUID();
        callerHasAnActiveAccountIn("EUR");
        when(transactionRepository.expenseTotalsByCategory(userId, "EUR", MARCH.atDay(1), MARCH.atEndOfMonth()))
                .thenReturn(List.of(
                        new CategoryTotal(foodId, "Food", new BigDecimal("80.00")),
                        new CategoryTotal(null, null, new BigDecimal("15.50"))));

        CategorySpendingReport report = service.spendingByCategory(userId, MARCH, null);

        assertThat(report.totalSpent()).isEqualByComparingTo("95.50");
        assertThat(report.categories()).hasSize(2);
        CategorySpendingReport.Entry food = report.categories().get(0);
        assertThat(food.categoryId()).isEqualTo(foodId);
        assertThat(food.percentage()).isEqualByComparingTo("83.8");
        CategorySpendingReport.Entry other = report.categories().get(1);
        assertThat(other.categoryId()).isNull();
        assertThat(other.categoryName()).isEqualTo("Uncategorised");
        assertThat(other.percentage()).isEqualByComparingTo("16.2");
    }

    @Test
    void aMonthWithoutSpendingHasNoCategoriesAndNoDivisionByZero() {
        callerHasAnActiveAccountIn("EUR");
        when(transactionRepository.expenseTotalsByCategory(any(), any(), any(), any())).thenReturn(List.of());

        CategorySpendingReport report = service.spendingByCategory(userId, MARCH, null);

        assertThat(report.categories()).isEmpty();
        assertThat(report.totalSpent()).isEqualByComparingTo("0");
    }
}
