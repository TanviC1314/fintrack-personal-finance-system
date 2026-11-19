package com.fintrack.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.account.entity.Account;
import com.fintrack.account.repository.AccountRepository;
import com.fintrack.support.Concurrency;
import com.fintrack.support.PostgresTestContainerConfig;
import com.fintrack.support.TestApi;
import com.fintrack.support.TestUsers;
import com.fintrack.support.TestUsers.Session;
import com.fintrack.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Checks the guarantee the whole design rests on: the account's cached balance always equals its
 * opening balance plus the net effect of its ledger, including under concurrent requests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainerConfig.class)
class TransactionConsistencyIntegrationTest {

    private static final String DAY = "2025-03-10";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private TransactionRepository transactionRepository;

    private TestApi api;
    private Session alice;

    @BeforeEach
    void setUp() throws Exception {
        api = new TestApi(mockMvc, objectMapper);
        alice = new TestUsers(mockMvc, objectMapper).registerAndLogin("Alice");
    }

    /** The invariant: current_balance = opening_balance + ledger total of non-reversed transactions. */
    private void assertBalanceMatchesLedger(UUID accountId) {
        Account account = accountRepository.findById(accountId).orElseThrow();
        BigDecimal fromLedger = account.getOpeningBalance().add(transactionRepository.ledgerTotal(accountId));

        assertThat(account.getCurrentBalance()).isEqualByComparingTo(fromLedger);
    }

    @Test
    void balanceMatchesTheLedgerAfterAMixOfCreatesEditsAndReversals() throws Exception {
        UUID account = api.createAccount(alice, "Mixed", "250.00");

        UUID salary = api.createTransaction(alice, account, "INCOME", "1200.50", "Salary", DAY);
        UUID rent = api.createTransaction(alice, account, "EXPENSE", "700.00", "Rent", DAY);
        UUID food = api.createTransaction(alice, account, "EXPENSE", "89.99", "Food", DAY);
        api.createTransaction(alice, account, "EXPENSE", "0.01", "Food", DAY);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/v1/transactions/" + food)
                .header(HttpHeaders.AUTHORIZATION, alice.authorization())
                .contentType("application/json")
                .content("{\"amount\":120.10,\"category\":\"Food\",\"transactionDate\":\"" + DAY + "\"}"));
        mockMvc.perform(delete("/api/v1/transactions/" + rent).header(HttpHeaders.AUTHORIZATION, alice.authorization()));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/v1/transactions/" + salary)
                .header(HttpHeaders.AUTHORIZATION, alice.authorization())
                .contentType("application/json")
                .content("{\"amount\":1000.00,\"transactionDate\":\"" + DAY + "\"}"));

        // 250.00 + 1000.00 (edited salary) - 120.10 (edited food) - 0.01 ; rent was reversed
        assertThat(api.balanceOf(alice, account)).isEqualByComparingTo("1129.89");
        assertBalanceMatchesLedger(account);
    }

    @Test
    void rejectedOperationsLeaveTheBalanceAndLedgerUntouched() throws Exception {
        UUID account = api.createAccount(alice, "Strict", "10.00");

        api.postTransaction(alice, account, "EXPENSE", "10.01", null, DAY);
        api.postTransaction(alice, account, "EXPENSE", "0", null, DAY);
        api.postTransaction(alice, account, "TRANSFER_OUT", "5.00", null, DAY);

        assertThat(api.balanceOf(alice, account)).isEqualByComparingTo("10.00");
        assertBalanceMatchesLedger(account);
    }

    @Test
    void concurrentExpensesCannotOverdrawTheAccount() throws Exception {
        UUID account = api.createAccount(alice, "Contended", "100.00");
        int attempts = 8; // each spends 30.00, so at most three can succeed

        List<Integer> statuses = runConcurrently(attempts, () ->
                api.postTransaction(alice, account, "EXPENSE", "30.00", "Race", DAY)
                        .andReturn().getResponse().getStatus());

        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(3);
        assertThat(statuses.stream().filter(s -> s == 422).count()).isEqualTo(attempts - 3);
        assertThat(api.balanceOf(alice, account)).isEqualByComparingTo("10.00");
        assertBalanceMatchesLedger(account);
    }

    @Test
    void concurrentReversalsOfOneTransactionRefundOnlyOnce() throws Exception {
        UUID account = api.createAccount(alice, "Refund", "100.00");
        UUID expense = api.createTransaction(alice, account, "EXPENSE", "40.00", "Oops", DAY);

        List<Integer> statuses = runConcurrently(6, () ->
                mockMvc.perform(delete("/api/v1/transactions/" + expense)
                                .header(HttpHeaders.AUTHORIZATION, alice.authorization()))
                        .andReturn().getResponse().getStatus());

        assertThat(statuses).allMatch(s -> s == 204);
        assertThat(api.balanceOf(alice, account)).isEqualByComparingTo("100.00");
        assertBalanceMatchesLedger(account);
    }

    @Test
    void concurrentIncomeAndExpenseOnOneAccountAllApplyExactlyOnce() throws Exception {
        UUID account = api.createAccount(alice, "Busy", "500.00");

        List<Integer> statuses = new ArrayList<>(runConcurrently(4, () ->
                api.postTransaction(alice, account, "INCOME", "10.00", null, DAY).andReturn().getResponse().getStatus()));
        statuses.addAll(runConcurrently(4, () ->
                api.postTransaction(alice, account, "EXPENSE", "5.00", null, DAY).andReturn().getResponse().getStatus()));

        assertThat(statuses).allMatch(s -> s == 201);
        assertThat(api.balanceOf(alice, account)).isEqualByComparingTo("520.00");
        assertBalanceMatchesLedger(account);
    }

    private static <T> List<T> runConcurrently(int threads, Callable<T> task) throws Exception {
        return Concurrency.runTogether(threads, task);
    }
}
