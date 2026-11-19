package com.fintrack.transaction.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.account.entity.Account;
import com.fintrack.account.entity.AccountType;
import com.fintrack.account.repository.AccountRepository;
import com.fintrack.category.entity.Category;
import com.fintrack.category.repository.CategoryRepository;
import com.fintrack.support.PostgresTestContainerConfig;
import com.fintrack.transaction.entity.Transaction;
import com.fintrack.transaction.entity.TransactionType;
import com.fintrack.transfer.entity.Transfer;
import com.fintrack.transfer.repository.TransferRepository;
import com.fintrack.user.entity.User;
import com.fintrack.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

/**
 * The dashboard totals are computed by SQL, so they are tested against a real PostgreSQL with data that
 * is deliberately full of things that must NOT be counted: reversed rows, transfer legs, another
 * currency and another user.
 *
 * <pre>
 * alice EUR account A:  INCOME 1000.00 05 Jan Salary | EXPENSE 50.00 10 Jan Food | EXPENSE 30.00 31 Jan Food
 *                       EXPENSE 20.00 01 Feb Transport | EXPENSE 999.00 20 Jan Food (reversed)
 * alice EUR account B:  EXPENSE 15.50 15 Jan (no category) | INCOME 200.00 10 Feb
 * alice transfer A to B 100.00 on 20 Jan (two ledger legs)
 * alice USD account U:  EXPENSE 500.00 12 Jan Food
 * bob   EUR account:    EXPENSE 777.00 10 Jan
 * </pre>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestContainerConfig.class)
class TransactionAggregationRepositoryTest {

    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TransferRepository transferRepository;

    private UUID alice;
    private UUID bob;
    private Account accountA;
    private Account accountB;
    private Category food;
    private Category transport;
    private Transaction grocery;
    private Transaction lunch;
    private Transaction bus;
    private Transaction freelance;

    @BeforeEach
    void seed() {
        alice = userRepository.saveAndFlush(new User("Alice", "a-" + UUID.randomUUID() + "@x.com", "h")).getId();
        bob = userRepository.saveAndFlush(new User("Bob", "b-" + UUID.randomUUID() + "@x.com", "h")).getId();
        accountA = account(alice, "A", "EUR", "100.00");
        accountB = account(alice, "B", "EUR", "50.00");
        Account accountU = account(alice, "U", "USD", "25.00");
        Account bobs = account(bob, "Bobs", "EUR", "10.00");
        Category salaryCategory = categoryRepository.saveAndFlush(new Category(alice, "Salary"));
        food = categoryRepository.saveAndFlush(new Category(alice, "Food"));
        transport = categoryRepository.saveAndFlush(new Category(alice, "Transport"));

        tx(accountA, TransactionType.INCOME, "1000.00", salaryCategory, "2025-01-05");
        grocery = tx(accountA, TransactionType.EXPENSE, "50.00", food, "2025-01-10");
        lunch = tx(accountA, TransactionType.EXPENSE, "30.00", food, "2025-01-31");
        bus = tx(accountA, TransactionType.EXPENSE, "20.00", transport, "2025-02-01");
        Transaction reversed = tx(accountA, TransactionType.EXPENSE, "999.00", food, "2025-01-20");
        reversed.markReversed(Instant.now());
        tx(accountB, TransactionType.EXPENSE, "15.50", null, "2025-01-15");
        freelance = tx(accountB, TransactionType.INCOME, "200.00", null, "2025-02-10");
        tx(accountU, TransactionType.EXPENSE, "500.00", food, "2025-01-12");
        tx(bobs, TransactionType.EXPENSE, "777.00", null, "2025-01-10");

        Transfer transfer = transferRepository.saveAndFlush(new Transfer(alice, accountA.getId(), accountB.getId(),
                new BigDecimal("100.00"), null, LocalDate.parse("2025-01-20"), "k-" + UUID.randomUUID()));
        transactionRepository.save(Transaction.transferLeg(accountA, TransactionType.TRANSFER_OUT,
                new BigDecimal("100.00"), null, LocalDate.parse("2025-01-20"), transfer.getId()));
        transactionRepository.save(Transaction.transferLeg(accountB, TransactionType.TRANSFER_IN,
                new BigDecimal("100.00"), null, LocalDate.parse("2025-01-20"), transfer.getId()));
        transactionRepository.flush();
    }

    private Account account(UUID userId, String name, String currency, String opening) {
        return accountRepository.saveAndFlush(
                new Account(userId, name, AccountType.CURRENT, currency, new BigDecimal(opening)));
    }

    private Transaction tx(Account account, TransactionType type, String amount, Category category, String date) {
        return transactionRepository.save(
                new Transaction(account, type, new BigDecimal(amount), category, null, LocalDate.parse(date)));
    }

    private static LocalDate d(String date) {
        return LocalDate.parse(date);
    }

    // ---- sums ----

    @Test
    void monthlyExpensesCountOnlyTheCallersNonReversedExpensesInThatCurrency() {
        BigDecimal january = transactionRepository.sumAmount(
                alice, "EUR", TransactionType.EXPENSE, d("2025-01-01"), d("2025-01-31"));

        assertThat(january).isEqualByComparingTo("95.50"); // 50 + 30 + 15.50, not the reversed 999, USD 500 or Bob's 777
    }

    @Test
    void monthlyIncomeIsIndependentOfExpensesAndTransfers() {
        assertThat(transactionRepository.sumAmount(
                alice, "EUR", TransactionType.INCOME, d("2025-01-01"), d("2025-01-31"))).isEqualByComparingTo("1000.00");
        assertThat(transactionRepository.sumAmount(
                alice, "EUR", TransactionType.INCOME, d("2025-02-01"), d("2025-02-28"))).isEqualByComparingTo("200.00");
    }

    @Test
    void transferLegsAreNotIncomeOrExpense() {
        assertThat(transactionRepository.sumAmount(
                alice, "EUR", TransactionType.TRANSFER_IN, d("2025-01-01"), d("2025-01-31"))).isEqualByComparingTo("100.00");
        // ...and the plain income/expense totals above already exclude them (1000 and 95.50, not 1100 or 195.50).
    }

    @Test
    void dateRangesAreInclusiveAtBothEnds() {
        assertThat(transactionRepository.sumAmount(
                alice, "EUR", TransactionType.EXPENSE, d("2025-01-10"), d("2025-01-10"))).isEqualByComparingTo("50.00");
        assertThat(transactionRepository.sumAmount(
                alice, "EUR", TransactionType.EXPENSE, d("2025-01-31"), d("2025-02-01"))).isEqualByComparingTo("50.00");
    }

    @Test
    void eachCurrencyIsSummedSeparately() {
        assertThat(transactionRepository.sumAmount(
                alice, "USD", TransactionType.EXPENSE, d("2025-01-01"), d("2025-01-31"))).isEqualByComparingTo("500.00");
    }

    @Test
    void aMonthWithNothingSumsToZeroNotNull() {
        assertThat(transactionRepository.sumAmount(
                alice, "EUR", TransactionType.EXPENSE, d("2025-03-01"), d("2025-03-31"))).isEqualByComparingTo("0");
    }

    @Test
    void aUserWithNoDataGetsZerosAndEmptyLists() {
        UUID nobody = userRepository.saveAndFlush(new User("N", "n-" + UUID.randomUUID() + "@x.com", "h")).getId();

        assertThat(transactionRepository.sumAmount(nobody, "EUR", TransactionType.EXPENSE, d("2025-01-01"), d("2025-12-31")))
                .isEqualByComparingTo("0");
        assertThat(transactionRepository.monthlyTotals(nobody, "EUR",
                List.of(TransactionType.INCOME, TransactionType.EXPENSE), d("2025-01-01"), d("2025-12-31"))).isEmpty();
        assertThat(transactionRepository.expenseTotalsByCategory(nobody, "EUR", d("2025-01-01"), d("2025-12-31"))).isEmpty();
        assertThat(transactionRepository.findRecent(nobody, "EUR", PageRequest.of(0, 10))).isEmpty();
        assertThat(accountRepository.sumActiveBalance(nobody, "EUR")).isEqualByComparingTo("0");
    }

    // ---- monthly trend ----

    @Test
    void monthlyTotalsGroupByMonthAndTypeAndSkipEmptyMonths() {
        List<MonthlyTypeTotal> totals = transactionRepository.monthlyTotals(alice, "EUR",
                List.of(TransactionType.INCOME, TransactionType.EXPENSE), d("2025-01-01"), d("2025-03-31"));

        assertThat(totals).extracting(t -> t.year() + "-" + t.month() + "-" + t.type() + "=" + t.total().toPlainString())
                .containsExactlyInAnyOrder(
                        "2025-1-INCOME=1000.00",
                        "2025-1-EXPENSE=95.50",
                        "2025-2-INCOME=200.00",
                        "2025-2-EXPENSE=20.00");
    }

    // ---- spending by category ----

    @Test
    void spendingByCategoryIsLargestFirstWithUncategorisedAsItsOwnRow() {
        List<CategoryTotal> january = transactionRepository.expenseTotalsByCategory(
                alice, "EUR", d("2025-01-01"), d("2025-01-31"));

        assertThat(january).hasSize(2);
        assertThat(january.get(0).categoryId()).isEqualTo(food.getId());
        assertThat(january.get(0).categoryName()).isEqualTo("Food");
        assertThat(january.get(0).total()).isEqualByComparingTo("80.00"); // reversed 999 and USD 500 are excluded
        assertThat(january.get(1).categoryId()).isNull();
        assertThat(january.get(1).categoryName()).isNull();
        assertThat(january.get(1).total()).isEqualByComparingTo("15.50");
    }

    @Test
    void spendingByCategoryRespectsTheDateRange() {
        List<CategoryTotal> february = transactionRepository.expenseTotalsByCategory(
                alice, "EUR", d("2025-02-01"), d("2025-02-28"));

        assertThat(february).singleElement().satisfies(row -> {
            assertThat(row.categoryId()).isEqualTo(transport.getId());
            assertThat(row.total()).isEqualByComparingTo("20.00");
        });
    }

    // ---- recent transactions ----

    @Test
    void recentTransactionsAreNewestFirstAndLimited() {
        List<Transaction> recent = transactionRepository.findRecent(alice, "EUR", PageRequest.of(0, 3));

        assertThat(recent).extracting(Transaction::getId).containsExactly(freelance.getId(), bus.getId(), lunch.getId());
    }

    @Test
    void recentTransactionsExcludeReversedOtherCurrenciesAndOtherUsers() {
        List<Transaction> all = transactionRepository.findRecent(alice, "EUR", PageRequest.of(0, 100));

        assertThat(all).noneMatch(Transaction::isReversed);
        assertThat(all).allMatch(t -> t.getAmount().compareTo(new BigDecimal("500.00")) != 0
                && t.getAmount().compareTo(new BigDecimal("777.00")) != 0);
        assertThat(all).extracting(Transaction::getType).contains(TransactionType.TRANSFER_IN, TransactionType.TRANSFER_OUT);
        // Six ordinary EUR rows (reversed, USD and Bob's are out) plus the two transfer legs.
        assertThat(all).hasSize(8);
    }

    @Test
    void recentTransactionsCarryTheirCategoryNameWithoutALazyLookup() {
        Transaction first = transactionRepository.findRecent(alice, "EUR", PageRequest.of(0, 10)).stream()
                .filter(t -> t.getId().equals(grocery.getId())).findFirst().orElseThrow();

        assertThat(first.getCategory().getName()).isEqualTo("Food");
    }

    // ---- balances ----

    @Test
    void totalBalanceSumsActiveAccountsInOneCurrency() {
        Account closed = account(alice, "Closed", "EUR", "40.00");
        closed.archive();
        accountRepository.saveAndFlush(closed);

        assertThat(accountRepository.sumActiveBalance(alice, "EUR")).isEqualByComparingTo("150.00"); // A 100 + B 50
        assertThat(accountRepository.sumActiveBalance(alice, "USD")).isEqualByComparingTo("25.00");
        assertThat(accountRepository.sumActiveBalance(bob, "EUR")).isEqualByComparingTo("10.00");
    }
}
