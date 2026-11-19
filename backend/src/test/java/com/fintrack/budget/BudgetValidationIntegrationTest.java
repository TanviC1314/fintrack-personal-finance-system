package com.fintrack.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.support.Concurrency;
import com.fintrack.support.PostgresTestContainerConfig;
import com.fintrack.support.TestApi;
import com.fintrack.support.TestUsers;
import com.fintrack.support.TestUsers.Session;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
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

/** What the budget API refuses, and the edge cases of how it counts spending. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainerConfig.class)
class BudgetValidationIntegrationTest {

    private static final String MARCH = "2025-03";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private TestApi api;
    private Session alice;
    private Session bob;
    private UUID account;
    private UUID food;

    @BeforeEach
    void setUp() throws Exception {
        api = new TestApi(mockMvc, objectMapper);
        TestUsers users = new TestUsers(mockMvc, objectMapper);
        alice = users.registerAndLogin("Alice");
        bob = users.registerAndLogin("Bob");
        account = api.createAccount(alice, "Main", "10000.00");
        food = api.categoryId(alice, "Food");
    }

    private ResultActions postBudget(Session as, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/budgets")
                .header(HttpHeaders.AUTHORIZATION, as.authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions createBudget(Session as, UUID category, String month, String currency, String amount)
            throws Exception {
        return postBudget(as, """
                {"categoryId":"%s","month":"%s","currency":"%s","amount":%s}
                """.formatted(category, month, currency, amount));
    }

    private UUID createdBudget(String amount) throws Exception {
        String body = createBudget(alice, food, MARCH, "EUR", amount).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private ResultActions putBudget(Session as, String id, String json) throws Exception {
        return mockMvc.perform(put("/api/v1/budgets/" + id)
                .header(HttpHeaders.AUTHORIZATION, as.authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions listBudgets(Session as, String query) throws Exception {
        return mockMvc.perform(get("/api/v1/budgets" + query).header(HttpHeaders.AUTHORIZATION, as.authorization()));
    }

    // ---- create: field validation ----

    @Test
    void missingFieldsAreAllReported() throws Exception {
        postBudget(alice, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='categoryId')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='month')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='currency')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='amount')]").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1", "-0.01", "0.001", "10.005", "12345678901234567890.00"})
    void invalidAmountsAreRejectedAndNoBudgetIsCreated(String amount) throws Exception {
        createBudget(alice, food, MARCH, "EUR", amount)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='amount')]").exists());

        listBudgets(alice, "?month=" + MARCH).andExpect(jsonPath("$", hasSize(0)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2025-13", "2025-00", "March", "2025-3", "2025-03-01", "03-2025", ""})
    void malformedMonthsAreRejected(String month) throws Exception {
        createBudget(alice, food, month, "EUR", "100").andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABC", "eur", "EURO", "E", "", "12 "})
    void invalidCurrenciesAreRejected(String currency) throws Exception {
        createBudget(alice, food, MARCH, currency, "100")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='currency')]").exists());
    }

    @Test
    void anAmountAsTextInsteadOfANumberIsABadRequest() throws Exception {
        postBudget(alice, "{\"categoryId\":\"%s\",\"month\":\"%s\",\"currency\":\"EUR\",\"amount\":\"lots\"}"
                .formatted(food, MARCH)).andExpect(status().isBadRequest());
    }

    // ---- create: month limits ----

    @Test
    void budgetsMayBeSetTwoYearsAheadButNotFurther() throws Exception {
        YearMonth now = YearMonth.now(ZoneOffset.UTC);

        createBudget(alice, food, now.plusMonths(24).toString(), "EUR", "100").andExpect(status().isCreated());
        createBudget(alice, api.categoryId(alice, "Other"), now.plusMonths(25).toString(), "EUR", "100")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Budgets can be set at most 24 months ahead"));
    }

    @Test
    void aPastMonthCanBeBudgetedAfterTheFact() throws Exception {
        createBudget(alice, food, "2019-06", "EUR", "100")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.month").value("2019-06"));
    }

    // ---- create: category ownership ----

    @Test
    void anUnknownCategoryIsNotFound() throws Exception {
        createBudget(alice, UUID.randomUUID(), MARCH, "EUR", "100").andExpect(status().isNotFound());
    }

    @Test
    void anotherUsersCategoryCannotBeBudgeted() throws Exception {
        UUID bobsCategory = api.categoryId(bob, "Bobs");

        createBudget(alice, bobsCategory, MARCH, "EUR", "100").andExpect(status().isNotFound());

        listBudgets(bob, "?month=" + MARCH).andExpect(jsonPath("$", hasSize(0)));
    }

    // ---- uniqueness ----

    @Test
    void aSecondBudgetForTheSameCategoryMonthAndCurrencyIsAConflict() throws Exception {
        createdBudget("100.00");

        createBudget(alice, food, MARCH, "EUR", "999.00").andExpect(status().isConflict());

        listBudgets(alice, "?month=" + MARCH)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].amount").value(100.00));
    }

    @Test
    void anotherMonthOrCurrencyIsADifferentBudget() throws Exception {
        createdBudget("100.00");

        createBudget(alice, food, "2025-04", "EUR", "100").andExpect(status().isCreated());
        createBudget(alice, food, MARCH, "USD", "100").andExpect(status().isCreated());
    }

    @Test
    void sixSimultaneousIdenticalRequestsCreateExactlyOneBudgetAndNeverAServerError() throws Exception {
        List<Integer> statuses = Concurrency.runTogether(6, () ->
                createBudget(alice, food, MARCH, "EUR", "100").andReturn().getResponse().getStatus());

        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(5);
        listBudgets(alice, "?month=" + MARCH).andExpect(jsonPath("$", hasSize(1)));
    }

    // ---- update ----

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "1.234", "\"abc\""})
    void invalidAmountsAreRejectedOnUpdateAndTheBudgetIsUnchanged(String amount) throws Exception {
        UUID id = createdBudget("100.00");

        putBudget(alice, id.toString(), "{\"amount\":" + amount + "}").andExpect(status().isBadRequest());

        listBudgets(alice, "?month=" + MARCH).andExpect(jsonPath("$[0].amount").value(100.00));
    }

    @Test
    void anUpdateWithoutAnAmountIsRejected() throws Exception {
        UUID id = createdBudget("100.00");

        putBudget(alice, id.toString(), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='amount')]").exists());
    }

    @Test
    void onlyTheAmountCanBeChangedEvenIfOtherFieldsAreSent() throws Exception {
        UUID id = createdBudget("100.00");
        UUID other = api.categoryId(alice, "Other");

        putBudget(alice, id.toString(), """
                {"amount":50.00,"categoryId":"%s","month":"2030-01","currency":"USD"}
                """.formatted(other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(50.00))
                .andExpect(jsonPath("$.categoryName").value("Food"))
                .andExpect(jsonPath("$.month").value(MARCH))
                .andExpect(jsonPath("$.currency").value("EUR"));
    }

    @Test
    void unknownAndMalformedBudgetIdsAreHandledCleanly() throws Exception {
        putBudget(alice, UUID.randomUUID().toString(), "{\"amount\":10}").andExpect(status().isNotFound());
        putBudget(alice, "not-a-uuid", "{\"amount\":10}").andExpect(status().isBadRequest());
    }

    // ---- listing parameters ----

    @Test
    void badListParametersAreBadRequests() throws Exception {
        listBudgets(alice, "?month=2025-13").andExpect(status().isBadRequest());
        listBudgets(alice, "?month=March").andExpect(status().isBadRequest());
        listBudgets(alice, "?exceededOnly=maybe").andExpect(status().isBadRequest());
    }

    @Test
    void filteringByACurrencyWithNoBudgetsReturnsAnEmptyList() throws Exception {
        createdBudget("100.00");

        listBudgets(alice, "?month=" + MARCH + "&currency=JPY").andExpect(jsonPath("$", hasSize(0)));
        listBudgets(alice, "?month=" + MARCH + "&currency=eur").andExpect(jsonPath("$", hasSize(1)));
    }

    // ---- what counts as spending ----

    @Test
    void incomeAndTransfersInACategoryDoNotCountAsSpending() throws Exception {
        UUID second = api.createAccount(alice, "Second", "0");
        createdBudget("100.00");
        api.createTransaction(alice, account, "INCOME", "500.00", "Food", "2025-03-05"); // refund-like income
        mockMvc.perform(post("/api/v1/transfers")
                        .header(HttpHeaders.AUTHORIZATION, alice.authorization())
                        .header("Idempotency-Key", "b-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\":\"%s\",\"destinationAccountId\":\"%s\",\"amount\":300.00,\"transferDate\":\"2025-03-06\"}"
                                .formatted(account, second)))
                .andExpect(status().isCreated());

        listBudgets(alice, "?month=" + MARCH)
                .andExpect(jsonPath("$[0].spent").value(0))
                .andExpect(jsonPath("$[0].exceeded").value(false));
    }

    @Test
    void spendingInAnotherCurrencyDoesNotCountAgainstAEuroBudget() throws Exception {
        createdBudget("100.00");
        String body = mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, alice.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Dollars\",\"type\":\"CASH\",\"currency\":\"USD\",\"openingBalance\":1000}"))
                .andReturn().getResponse().getContentAsString();
        UUID dollars = UUID.fromString(objectMapper.readTree(body).get("id").asText());
        api.createTransaction(alice, dollars, "EXPENSE", "400.00", "Food", "2025-03-05");
        api.createTransaction(alice, account, "EXPENSE", "10.00", "Food", "2025-03-05");

        listBudgets(alice, "?month=" + MARCH)
                .andExpect(jsonPath("$[0].spent").value(10.00))
                .andExpect(jsonPath("$[0].exceeded").value(false));
    }

    @Test
    void aTinyBudgetAgainstBigSpendingGivesAHugeButExactPercentage() throws Exception {
        createBudget(alice, food, MARCH, "EUR", "0.01").andExpect(status().isCreated());
        api.createTransaction(alice, account, "EXPENSE", "5000.00", "Food", "2025-03-05");

        listBudgets(alice, "?month=" + MARCH)
                .andExpect(jsonPath("$[0].utilisationPercent").value(50000000.0))
                .andExpect(jsonPath("$[0].exceeded").value(true));
    }

    @Test
    void spendingOnTheFirstAndLastDayOfTheMonthCountsButTheNeighbouringDaysDoNot() throws Exception {
        createdBudget("1000.00");
        api.createTransaction(alice, account, "EXPENSE", "1.00", "Food", "2025-02-28");
        api.createTransaction(alice, account, "EXPENSE", "10.00", "Food", "2025-03-01");
        api.createTransaction(alice, account, "EXPENSE", "20.00", "Food", "2025-03-31");
        api.createTransaction(alice, account, "EXPENSE", "2.00", "Food", "2025-04-01");

        listBudgets(alice, "?month=" + MARCH).andExpect(jsonPath("$[0].spent").value(30.00));
    }

    // ---- authentication ----

    @Test
    void budgetEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/budgets")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/budgets").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/budgets/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnauthorized());
    }
}
