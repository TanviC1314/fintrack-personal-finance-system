package com.fintrack.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Transfers under simultaneous load. Each test ends by checking the core invariant on every account
 * involved: opening balance + ledger total = cached balance, so money was neither created nor lost.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainerConfig.class)
class TransferConcurrencyIntegrationTest {

    private static final String DAY = "2025-03-10";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestApi api;
    private Session alice;

    @BeforeEach
    void setUp() throws Exception {
        api = new TestApi(mockMvc, objectMapper);
        alice = new TestUsers(mockMvc, objectMapper).registerAndLogin("Alice");
    }

    private Callable<Integer> transfer(String key, UUID from, UUID to, String amount) {
        return () -> mockMvc.perform(post("/api/v1/transfers")
                        .header(HttpHeaders.AUTHORIZATION, alice.authorization())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\":\"%s\",\"destinationAccountId\":\"%s\",\"amount\":%s}"
                                .formatted(from, to, amount)))
                .andReturn().getResponse().getStatus();
    }

    private Callable<Integer> expense(UUID account, String amount) {
        return () -> api.postTransaction(alice, account, "EXPENSE", amount, "Race", DAY)
                .andReturn().getResponse().getStatus();
    }

    private static String key() {
        return "k-" + UUID.randomUUID();
    }

    private static long count(List<Integer> statuses, int status) {
        return statuses.stream().filter(s -> s == status).count();
    }

    private void assertBalanceMatchesLedger(UUID accountId) {
        Account account = accountRepository.findById(accountId).orElseThrow();
        BigDecimal fromLedger = account.getOpeningBalance().add(transactionRepository.ledgerTotal(accountId));

        assertThat(account.getCurrentBalance()).isEqualByComparingTo(fromLedger);
    }

    private BigDecimal totalOf(UUID... accounts) throws Exception {
        BigDecimal total = BigDecimal.ZERO;
        for (UUID account : accounts) {
            total = total.add(api.balanceOf(alice, account));
        }
        return total;
    }

    @Test
    void transfersInOppositeDirectionsAtTheSameTimeNeitherDeadlockNorLoseMoney() throws Exception {
        UUID a = api.createAccount(alice, "A", "1000.00");
        UUID b = api.createAccount(alice, "B", "1000.00");
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tasks.add(transfer(key(), a, b, "10.00"));
            tasks.add(transfer(key(), b, a, "3.00"));
        }

        List<Integer> statuses = Concurrency.runTogether(tasks);

        assertThat(statuses).allMatch(s -> s == 201);
        assertThat(api.balanceOf(alice, a)).isEqualByComparingTo("972.00");  // -40 + 12
        assertThat(api.balanceOf(alice, b)).isEqualByComparingTo("1028.00"); // +40 - 12
        assertThat(totalOf(a, b)).isEqualByComparingTo("2000.00");
        assertBalanceMatchesLedger(a);
        assertBalanceMatchesLedger(b);
    }

    @Test
    void identicalRetriesAtTheSameTimeMoveTheMoneyExactlyOnce() throws Exception {
        UUID a = api.createAccount(alice, "A", "100.00");
        UUID b = api.createAccount(alice, "B", "0");
        String sharedKey = key();

        List<Integer> statuses = Concurrency.runTogether(8, transfer(sharedKey, a, b, "30.00"));

        assertThat(count(statuses, 201)).isEqualTo(1);
        assertThat(count(statuses, 200)).isEqualTo(7);
        assertThat(api.balanceOf(alice, a)).isEqualByComparingTo("70.00");
        assertThat(api.balanceOf(alice, b)).isEqualByComparingTo("30.00");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM transfers WHERE idempotency_key = ?", Integer.class, sharedKey)).isEqualTo(1);
        assertBalanceMatchesLedger(a);
        assertBalanceMatchesLedger(b);
    }

    @Test
    void oneKeyUsedForDifferentAmountsAtTheSameTimeLetsExactlyOneWin() throws Exception {
        UUID a = api.createAccount(alice, "A", "100.00");
        UUID b = api.createAccount(alice, "B", "0");
        String sharedKey = key();
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            tasks.add(transfer(sharedKey, a, b, i + ".00"));
        }

        List<Integer> statuses = Concurrency.runTogether(tasks);

        assertThat(count(statuses, 201)).isEqualTo(1);
        assertThat(count(statuses, 409)).isEqualTo(5);
        assertThat(totalOf(a, b)).isEqualByComparingTo("100.00");
        assertBalanceMatchesLedger(a);
        assertBalanceMatchesLedger(b);
    }

    @Test
    void competingTransfersCannotOverdrawTheSourceAccount() throws Exception {
        UUID a = api.createAccount(alice, "A", "100.00");
        UUID b = api.createAccount(alice, "B", "0");
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(transfer(key(), a, b, "30.00")); // each has its own key: eight different transfers
        }

        List<Integer> statuses = Concurrency.runTogether(tasks);

        assertThat(count(statuses, 201)).isEqualTo(3);
        assertThat(count(statuses, 422)).isEqualTo(5);
        assertThat(api.balanceOf(alice, a)).isEqualByComparingTo("10.00");
        assertThat(api.balanceOf(alice, b)).isEqualByComparingTo("90.00");
        assertBalanceMatchesLedger(a);
        assertBalanceMatchesLedger(b);
    }

    @Test
    void transfersAndExpensesCompetingForTheSameMoneyShareItWithoutOverdrawing() throws Exception {
        UUID a = api.createAccount(alice, "A", "100.00");
        UUID b = api.createAccount(alice, "B", "0");
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tasks.add(transfer(key(), a, b, "30.00"));
            tasks.add(expense(a, "30.00"));
        }

        List<Integer> statuses = Concurrency.runTogether(tasks);

        assertThat(count(statuses, 201)).isEqualTo(3);
        assertThat(count(statuses, 422)).isEqualTo(5);
        assertThat(api.balanceOf(alice, a)).isEqualByComparingTo("10.00");
        assertBalanceMatchesLedger(a);
        assertBalanceMatchesLedger(b);
    }
}
