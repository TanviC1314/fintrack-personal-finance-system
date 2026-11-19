package com.fintrack.budget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    private static final YearMonth MARCH = YearMonth.of(2026, 3);

    private final UUID userId = UUID.randomUUID();
    private final UUID foodId = UUID.randomUUID();
    private final UUID travelId = UUID.randomUUID();

    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private CategoryService categoryService;
    @Mock
    private TransactionRepository transactionRepository;

    private BudgetService service;
    private Category food;
    private Category travel;

    @BeforeEach
    void setUp() {
        // "Now" is 15 March 2026.
        service = new BudgetService(budgetRepository, categoryService, transactionRepository,
                Clock.fixed(Instant.parse("2026-03-15T12:00:00Z"), ZoneOffset.UTC));
        food = category(foodId, "Food");
        travel = category(travelId, "Travel");
    }

    private Category category(UUID id, String name) {
        Category category = new Category(userId, name);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private Budget budget(Category category, String currency, String amount) {
        Budget budget = new Budget(userId, category, currency, MARCH.atDay(1), new BigDecimal(amount));
        ReflectionTestUtils.setField(budget, "id", UUID.randomUUID());
        return budget;
    }

    private void spent(String currency, CategoryTotal... totals) {
        when(transactionRepository.expenseTotalsByCategory(userId, currency, MARCH.atDay(1), MARCH.atEndOfMonth()))
                .thenReturn(List.of(totals));
    }

    private CreateBudgetRequest request(UUID categoryId, YearMonth month, String amount) {
        return new CreateBudgetRequest(categoryId, month, "EUR", new BigDecimal(amount));
    }

    // ---- utilisation arithmetic ----

    @Test
    void aBudgetPartlyUsedShowsSpentRemainingAndPercentage() {
        Budget budget = budget(food, "EUR", "200.00");
        when(budgetRepository.findAllByUserIdAndMonthOrderByCategoryNameAscCurrencyAsc(userId, MARCH.atDay(1)))
                .thenReturn(List.of(budget));
        spent("EUR", new CategoryTotal(foodId, "Food", new BigDecimal("80.00")));

        BudgetResponse response = service.list(userId, MARCH, null, false).get(0);

        assertThat(response.spent()).isEqualByComparingTo("80.00");
        assertThat(response.remaining()).isEqualByComparingTo("120.00");
        assertThat(response.utilisationPercent()).isEqualByComparingTo("40.0");
        assertThat(response.exceeded()).isFalse();
        assertThat(response.month()).isEqualTo("2026-03");
        assertThat(response.categoryName()).isEqualTo("Food");
    }

    @Test
    void spendingExactlyTheBudgetIsFullUseButNotExceeded() {
        Budget budget = budget(food, "EUR", "200.00");
        when(budgetRepository.findAllByUserIdAndMonthOrderByCategoryNameAscCurrencyAsc(userId, MARCH.atDay(1)))
                .thenReturn(List.of(budget));
        spent("EUR", new CategoryTotal(foodId, "Food", new BigDecimal("200.00")));

        BudgetResponse response = service.list(userId, MARCH, null, false).get(0);

        assertThat(response.utilisationPercent()).isEqualByComparingTo("100.0");
        assertThat(response.remaining()).isEqualByComparingTo("0");
        assertThat(response.exceeded()).isFalse();
    }

    @Test
    void spendingOneCentOverTheBudgetIsExceededWithNegativeRemaining() {
        Budget budget = budget(food, "EUR", "200.00");
        when(budgetRepository.findAllByUserIdAndMonthOrderByCategoryNameAscCurrencyAsc(userId, MARCH.atDay(1)))
                .thenReturn(List.of(budget));
        spent("EUR", new CategoryTotal(foodId, "Food", new BigDecimal("200.01")));

        BudgetResponse response = service.list(userId, MARCH, null, false).get(0);

        assertThat(response.exceeded()).isTrue();
        assertThat(response.remaining()).isEqualByComparingTo("-0.01");
        assertThat(response.utilisationPercent()).isEqualByComparingTo("100.0"); // 100.005 rounds to one decimal
    }

    @Test
    void utilisationCanExceedOneHundredPercent() {
        Budget budget = budget(food, "EUR", "200.00");
        when(budgetRepository.findAllByUserIdAndMonthOrderByCategoryNameAscCurrencyAsc(userId, MARCH.atDay(1)))
                .thenReturn(List.of(budget));
        spent("EUR", new CategoryTotal(foodId, "Food", new BigDecimal("250.00")));

        assertThat(service.list(userId, MARCH, null, false).get(0).utilisationPercent()).isEqualByComparingTo("125.0");
    }

    @Test
    void aCategoryWithNoSpendingIsAtZeroPercent() {
        Budget budget = budget(travel, "EUR", "300.00");
        when(budgetRepository.findAllByUserIdAndMonthOrderByCategoryNameAscCurrencyAsc(userId, MARCH.atDay(1)))
                .thenReturn(List.of(budget));
        spent("EUR", new CategoryTotal(foodId, "Food", new BigDecimal("80.00")), new CategoryTotal(null, null, new BigDecimal("5.00")));

        BudgetResponse response = service.list(userId, MARCH, null, false).get(0);

        assertThat(response.spent()).isEqualByComparingTo("0");
        assertThat(response.utilisationPercent()).isEqualByComparingTo("0.0");
        assertThat(response.exceeded()).isFalse();
    }

    // ---- listing ----

    @Test
    void listDefaultsToTheCurrentMonth() {
        when(budgetRepository.findAllByUserIdAndMonthOrderByCategoryNameAscCurrencyAsc(userId, LocalDate.of(2026, 3, 1)))
                .thenReturn(List.of());

        assertThat(service.list(userId, null, null, false)).isEmpty();
    }

    @Test
    void oneAggregateQueryServesAllBudgetsOfACurrency() {
        Budget foodBudget = budget(food, "EUR", "200.00");
        Budget travelBudget = budget(travel, "EUR", "300.00");
        when(budgetRepository.findAllByUserIdAndMonthOrderByCategoryNameAscCurrencyAsc(userId, MARCH.atDay(1)))
                .thenReturn(List.of(foodBudget, travelBudget));
        spent("EUR", new CategoryTotal(foodId, "Food", new BigDecimal("10.00")),
                new CategoryTotal(travelId, "Travel", new BigDecimal("20.00")));

        List<BudgetResponse> responses = service.list(userId, MARCH, null, false);

        assertThat(responses).extracting(BudgetResponse::spent).usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("10.00"), new BigDecimal("20.00"));
        verify(transactionRepository, times(1)).expenseTotalsByCategory(any(), any(), any(), any());
    }

    @Test
    void eachCurrencyIsMeasuredAgainstItsOwnSpending() {
        Budget euro = budget(food, "EUR", "100.00");
        Budget dollar = budget(food, "USD", "100.00");
        when(budgetRepository.findAllByUserIdAndMonthOrderByCategoryNameAscCurrencyAsc(userId, MARCH.atDay(1)))
                .thenReturn(List.of(euro, dollar));
        spent("EUR", new CategoryTotal(foodId, "Food", new BigDecimal("30.00")));
        spent("USD", new CategoryTotal(foodId, "Food", new BigDecimal("150.00")));

        List<BudgetResponse> responses = service.list(userId, MARCH, null, false);

        assertThat(responses.get(0).exceeded()).isFalse();
        assertThat(responses.get(1).exceeded()).isTrue();
    }

    @Test
    void exceededOnlyKeepsJustTheOverspentBudgets() {
        Budget fine = budget(food, "EUR", "200.00");
        Budget over = budget(travel, "EUR", "100.00");
        when(budgetRepository.findAllByUserIdAndMonthOrderByCategoryNameAscCurrencyAsc(userId, MARCH.atDay(1)))
                .thenReturn(List.of(fine, over));
        spent("EUR", new CategoryTotal(foodId, "Food", new BigDecimal("50.00")),
                new CategoryTotal(travelId, "Travel", new BigDecimal("101.00")));

        assertThat(service.list(userId, MARCH, null, true)).extracting(BudgetResponse::categoryName)
                .containsExactly("Travel");
    }

    @Test
    void currencyFilterIgnoresCase() {
        Budget euro = budget(food, "EUR", "100.00");
        Budget dollar = budget(food, "USD", "100.00");
        when(budgetRepository.findAllByUserIdAndMonthOrderByCategoryNameAscCurrencyAsc(userId, MARCH.atDay(1)))
                .thenReturn(List.of(euro, dollar));
        spent("USD");

        assertThat(service.list(userId, MARCH, "usd", false)).extracting(BudgetResponse::currency).containsExactly("USD");
    }

    // ---- create ----

    @Test
    void createStoresTheFirstDayOfTheMonthForTheCallersCategory() {
        when(categoryService.getOwned(userId, foodId)).thenReturn(food);
        when(budgetRepository.saveAndFlush(any(Budget.class))).thenAnswer(inv -> {
            Budget saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });
        spent("EUR", new CategoryTotal(foodId, "Food", new BigDecimal("40.00")));

        BudgetResponse created = service.create(userId, request(foodId, MARCH, "200"));

        assertThat(created.amount()).isEqualTo(new BigDecimal("200.00"));
        assertThat(created.spent()).isEqualByComparingTo("40.00");
        org.mockito.ArgumentCaptor<Budget> saved = org.mockito.ArgumentCaptor.forClass(Budget.class);
        verify(budgetRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getMonth()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(saved.getValue().getUserId()).isEqualTo(userId);
    }

    @Test
    void aSecondBudgetForTheSameCategoryMonthAndCurrencyIsAConflict() {
        when(categoryService.getOwned(userId, foodId)).thenReturn(food);
        when(budgetRepository.saveAndFlush(any(Budget.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.create(userId, request(foodId, MARCH, "200")))
                .isInstanceOf(BudgetAlreadyExistsException.class);
    }

    @Test
    void aCategoryTheCallerDoesNotOwnIsNotFoundAndNothingIsSaved() {
        when(categoryService.getOwned(userId, foodId)).thenThrow(new ResourceNotFoundException("Category not found"));

        assertThatThrownBy(() -> service.create(userId, request(foodId, MARCH, "200")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(budgetRepository, never()).saveAndFlush(any());
    }

    @Test
    void budgetsCanBeSetUpTo24MonthsAheadButNotFurther() {
        when(categoryService.getOwned(userId, foodId)).thenReturn(food);
        when(budgetRepository.saveAndFlush(any(Budget.class))).thenAnswer(inv -> {
            Budget saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });
        when(transactionRepository.expenseTotalsByCategory(any(), any(), any(), any())).thenReturn(List.of());

        service.create(userId, request(foodId, MARCH.plusMonths(24), "10"));

        assertThatThrownBy(() -> service.create(userId, request(foodId, MARCH.plusMonths(25), "10")))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void pastMonthsCanBeBudgetedRetrospectively() {
        when(categoryService.getOwned(userId, foodId)).thenReturn(food);
        when(budgetRepository.saveAndFlush(any(Budget.class))).thenAnswer(inv -> {
            Budget saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });
        when(transactionRepository.expenseTotalsByCategory(any(), any(), any(), any())).thenReturn(List.of());

        assertThat(service.create(userId, request(foodId, YearMonth.of(2020, 1), "10")).month()).isEqualTo("2020-01");
    }

    // ---- update ----

    @Test
    void updateChangesTheAmountAndRecomputesUtilisation() {
        Budget budget = budget(food, "EUR", "200.00");
        UUID id = budget.getId();
        when(budgetRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.of(budget));
        spent("EUR", new CategoryTotal(foodId, "Food", new BigDecimal("80.00")));

        BudgetResponse updated = service.updateAmount(userId, id, new BigDecimal("100"));

        assertThat(updated.amount()).isEqualTo(new BigDecimal("100.00"));
        assertThat(updated.utilisationPercent()).isEqualByComparingTo("80.0");
        verify(budgetRepository).saveAndFlush(budget);
    }

    @Test
    void updateOfABudgetTheCallerDoesNotOwnIsNotFound() {
        UUID id = UUID.randomUUID();
        when(budgetRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateAmount(userId, id, BigDecimal.TEN))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
