package com.fintrack.dashboard;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.support.PostgresTestContainerConfig;
import com.fintrack.support.TestApi;
import com.fintrack.support.TestUsers;
import com.fintrack.support.TestUsers.Session;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Fixture for Alice (current account EUR 1000.00 opened first, savings EUR 0, a USD account with 200.00):
 * <pre>
 *   Feb  10  INCOME   100.00            Feb  11  EXPENSE   40.00 Food
 *   Mar  03  INCOME  3000.00 Salary     Mar  05  EXPENSE  120.50 Food
 *   Mar  12  EXPENSE  79.50 Food        Mar  20  EXPENSE   50.00 Transport
 *   Mar  21  EXPENSE  10.00 (none)      Mar  15  EXPENSE  300.00 Food, then reversed
 *   Mar  25  TRANSFER 500.00 current to savings
 *   Mar  04  INCOME    70.00 on the USD account
 * </pre>
 * Current balance: 1000 + 100 - 40 + 3000 - 120.50 - 79.50 - 50 - 10 - 500 = 3300.00; savings 500.00; EUR total 3800.00.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainerConfig.class)
class DashboardApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private TestApi api;
    private Session alice;
    private Session bob;
    private UUID current;
    private UUID savings;

    @BeforeEach
    void setUp() throws Exception {
        api = new TestApi(mockMvc, objectMapper);
        TestUsers users = new TestUsers(mockMvc, objectMapper);
        alice = users.registerAndLogin("Alice");
        bob = users.registerAndLogin("Bob");

        current = api.createAccount(alice, "Current", "1000.00");
        savings = api.createAccount(alice, "Savings", "0");
        UUID dollars = createUsdAccount();

        api.createTransaction(alice, current, "INCOME", "100.00", null, "2025-02-10");
        api.createTransaction(alice, current, "EXPENSE", "40.00", "Food", "2025-02-11");
        api.createTransaction(alice, current, "INCOME", "3000.00", "Salary", "2025-03-03");
        api.createTransaction(alice, current, "EXPENSE", "120.50", "Food", "2025-03-05");
        api.createTransaction(alice, current, "EXPENSE", "79.50", "Food", "2025-03-12");
        api.createTransaction(alice, current, "EXPENSE", "50.00", "Transport", "2025-03-20");
        api.createTransaction(alice, current, "EXPENSE", "10.00", null, "2025-03-21");
        UUID mistake = api.createTransaction(alice, current, "EXPENSE", "300.00", "Food", "2025-03-15");
        mockMvc.perform(delete("/api/v1/transactions/" + mistake).header(HttpHeaders.AUTHORIZATION, alice.authorization()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/transfers")
                        .header(HttpHeaders.AUTHORIZATION, alice.authorization())
                        .header("Idempotency-Key", "dash-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\":\"%s\",\"destinationAccountId\":\"%s\",\"amount\":500.00,\"transferDate\":\"2025-03-25\"}"
                                .formatted(current, savings)))
                .andExpect(status().isCreated());
        api.createTransaction(alice, dollars, "INCOME", "70.00", null, "2025-03-04");
    }

    private UUID createUsdAccount() throws Exception {
        String body = mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, alice.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Dollars\",\"type\":\"CASH\",\"currency\":\"USD\",\"openingBalance\":200.00}"))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private ResultActions dashboard(Session as, String path) throws Exception {
        return mockMvc.perform(get("/api/v1/dashboard" + path).header(HttpHeaders.AUTHORIZATION, as.authorization()));
    }

    // ---- summary ----

    @Test
    void summaryReportsTheMonthsIncomeExpensesSavingsAndTheTotalBalance() throws Exception {
        dashboard(alice, "/summary?month=2025-03")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.month").value("2025-03"))
                .andExpect(jsonPath("$.totalBalance").value(3800.00))
                .andExpect(jsonPath("$.monthlyIncome").value(3000.00))
                .andExpect(jsonPath("$.monthlyExpenses").value(260.00)) // the reversed 300 and the transfer are not spending
                .andExpect(jsonPath("$.monthlySavings").value(2740.00));
    }

    @Test
    void anotherMonthHasItsOwnFigures() throws Exception {
        dashboard(alice, "/summary?month=2025-02")
                .andExpect(jsonPath("$.monthlyIncome").value(100.00))
                .andExpect(jsonPath("$.monthlyExpenses").value(40.00))
                .andExpect(jsonPath("$.monthlySavings").value(60.00));
        dashboard(alice, "/summary?month=2024-01")
                .andExpect(jsonPath("$.monthlyIncome").value(0))
                .andExpect(jsonPath("$.monthlyExpenses").value(0));
    }

    @Test
    void theRecentListIsNewestFirstIncludesTransferLegsAndLeavesOutReversedAndOtherCurrencies() throws Exception {
        dashboard(alice, "/summary?month=2025-03")
                .andExpect(jsonPath("$.recentTransactions", hasSize(9)))
                .andExpect(jsonPath("$.recentTransactions[0].transactionDate").value("2025-03-25"))
                .andExpect(jsonPath("$.recentTransactions[2].amount").value(10.00))
                .andExpect(jsonPath("$.recentTransactions[?(@.reversedAt != null)]").isEmpty())
                .andExpect(jsonPath("$.recentTransactions[?(@.amount == 70.00)]").isEmpty());
    }

    @Test
    void anotherCurrencyIsSummarisedOnItsOwn() throws Exception {
        dashboard(alice, "/summary?month=2025-03&currency=usd")
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.totalBalance").value(270.00))
                .andExpect(jsonPath("$.monthlyIncome").value(70.00))
                .andExpect(jsonPath("$.monthlyExpenses").value(0));
    }

    @Test
    void withoutParametersTheCurrentMonthIsUsed() throws Exception {
        dashboard(alice, "/summary")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month", matchesPattern("\\d{4}-\\d{2}")))
                .andExpect(jsonPath("$.currency").value("EUR"));
    }

    @Test
    void aUserWithoutAccountsGetsAnEmptyDashboard() throws Exception {
        dashboard(bob, "/summary?month=2025-03")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").doesNotExist())
                .andExpect(jsonPath("$.totalBalance").value(0))
                .andExpect(jsonPath("$.recentTransactions", hasSize(0)));
        dashboard(bob, "/categories?month=2025-03").andExpect(jsonPath("$.categories", hasSize(0)));
    }

    @Test
    void figuresNeverIncludeAnotherUsersData() throws Exception {
        UUID bobsAccount = api.createAccount(bob, "Bobs", "5000.00");
        api.createTransaction(bob, bobsAccount, "EXPENSE", "1234.00", "Food", "2025-03-10");

        dashboard(alice, "/summary?month=2025-03").andExpect(jsonPath("$.monthlyExpenses").value(260.00));
        dashboard(bob, "/summary?month=2025-03")
                .andExpect(jsonPath("$.monthlyExpenses").value(1234.00))
                .andExpect(jsonPath("$.totalBalance").value(3766.00));
    }

    // ---- monthly trend ----

    @Test
    void trendListsConsecutiveMonthsOldestFirstWithEmptyMonthsAsZero() throws Exception {
        dashboard(alice, "/monthly?months=3&to=2025-03")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.months", hasSize(3)))
                .andExpect(jsonPath("$.months[0].month").value("2025-01"))
                .andExpect(jsonPath("$.months[0].income").value(0))
                .andExpect(jsonPath("$.months[1].month").value("2025-02"))
                .andExpect(jsonPath("$.months[1].income").value(100.00))
                .andExpect(jsonPath("$.months[1].expenses").value(40.00))
                .andExpect(jsonPath("$.months[1].savings").value(60.00))
                .andExpect(jsonPath("$.months[2].income").value(3000.00))
                .andExpect(jsonPath("$.months[2].expenses").value(260.00))
                .andExpect(jsonPath("$.months[2].savings").value(2740.00));
    }

    @Test
    void trendDefaultsToSixMonths() throws Exception {
        dashboard(alice, "/monthly").andExpect(jsonPath("$.months", hasSize(6)));
    }

    // ---- categories ----

    @Test
    void spendingByCategoryIsLargestFirstWithSharesAndAnUncategorisedRow() throws Exception {
        dashboard(alice, "/categories?month=2025-03")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpent").value(260.00))
                .andExpect(jsonPath("$.categories", hasSize(3)))
                .andExpect(jsonPath("$.categories[0].categoryName").value("Food"))
                .andExpect(jsonPath("$.categories[0].amount").value(200.00))
                .andExpect(jsonPath("$.categories[0].percentage").value(76.9))
                .andExpect(jsonPath("$.categories[1].categoryName").value("Transport"))
                .andExpect(jsonPath("$.categories[1].percentage").value(19.2))
                .andExpect(jsonPath("$.categories[2].categoryName").value("Uncategorised"))
                .andExpect(jsonPath("$.categories[2].categoryId").doesNotExist())
                .andExpect(jsonPath("$.categories[2].percentage").value(3.8));
    }

    // ---- bad input and authentication ----

    @ParameterizedTest
    @ValueSource(strings = {"/summary?month=2025-13", "/summary?month=March", "/summary?month=2025-3-1",
            "/summary?currency=EURO", "/summary?currency=E1", "/monthly?months=0", "/monthly?months=25",
            "/monthly?months=abc", "/monthly?to=nope", "/categories?month=bad", "/categories?currency=1"})
    void invalidParametersAreBadRequests(String path) throws Exception {
        dashboard(alice, path).andExpect(status().isBadRequest());
    }

    @Test
    void everyDashboardEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/dashboard/monthly")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/dashboard/categories")).andExpect(status().isUnauthorized());
    }
}
