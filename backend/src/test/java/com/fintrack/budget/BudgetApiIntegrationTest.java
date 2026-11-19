package com.fintrack.budget;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainerConfig.class)
class BudgetApiIntegrationTest {

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
    private UUID transport;

    @BeforeEach
    void setUp() throws Exception {
        api = new TestApi(mockMvc, objectMapper);
        TestUsers users = new TestUsers(mockMvc, objectMapper);
        alice = users.registerAndLogin("Alice");
        bob = users.registerAndLogin("Bob");
        account = api.createAccount(alice, "Main", "5000.00");
        food = api.categoryId(alice, "Food");
        transport = api.categoryId(alice, "Transport");
    }

    private ResultActions createBudget(Session as, UUID category, String month, String currency, String amount)
            throws Exception {
        return mockMvc.perform(post("/api/v1/budgets")
                .header(HttpHeaders.AUTHORIZATION, as.authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"categoryId":"%s","month":"%s","currency":"%s","amount":%s}
                        """.formatted(category, month, currency, amount)));
    }

    private UUID createdBudget(Session as, UUID category, String month, String amount) throws Exception {
        String body = createBudget(as, category, month, "EUR", amount).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private ResultActions listBudgets(Session as, String query) throws Exception {
        return mockMvc.perform(get("/api/v1/budgets" + query).header(HttpHeaders.AUTHORIZATION, as.authorization()));
    }

    private void spend(String categoryName, String amount, String date) throws Exception {
        api.createTransaction(alice, account, "EXPENSE", amount, categoryName, date);
    }

    @Test
    void createReturnsTheBudgetWithNothingSpentYet() throws Exception {
        createBudget(alice, food, MARCH, "EUR", "200.00")
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("/api/v1/budgets/")))
                .andExpect(jsonPath("$.categoryName").value("Food"))
                .andExpect(jsonPath("$.month").value(MARCH))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.amount").value(200.00))
                .andExpect(jsonPath("$.spent").value(0))
                .andExpect(jsonPath("$.remaining").value(200.00))
                .andExpect(jsonPath("$.utilisationPercent").value(0.0))
                .andExpect(jsonPath("$.exceeded").value(false));
    }

    @Test
    void utilisationFollowsTheMonthsRealSpending() throws Exception {
        createdBudget(alice, food, MARCH, "200.00");
        spend("Food", "120.50", "2025-03-05");
        spend("Food", "30.00", "2025-03-28");
        spend("Food", "999.00", "2025-02-28");   // another month
        spend("Transport", "40.00", "2025-03-10"); // another category

        listBudgets(alice, "?month=" + MARCH)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].spent").value(150.50))
                .andExpect(jsonPath("$[0].remaining").value(49.50))
                .andExpect(jsonPath("$[0].utilisationPercent").value(75.3))
                .andExpect(jsonPath("$[0].exceeded").value(false));
    }

    @Test
    void reversingAnExpenseFreesUpTheBudget() throws Exception {
        createdBudget(alice, food, MARCH, "100.00");
        UUID big = api.createTransaction(alice, account, "EXPENSE", "90.00", "Food", "2025-03-05");
        listBudgets(alice, "?month=" + MARCH).andExpect(jsonPath("$[0].utilisationPercent").value(90.0));

        mockMvc.perform(delete("/api/v1/transactions/" + big).header(HttpHeaders.AUTHORIZATION, alice.authorization()))
                .andExpect(status().isNoContent());

        listBudgets(alice, "?month=" + MARCH).andExpect(jsonPath("$[0].spent").value(0));
    }

    @Test
    void anOverspentBudgetIsFlaggedAndCanBeListedOnItsOwn() throws Exception {
        createdBudget(alice, food, MARCH, "100.00");
        createdBudget(alice, transport, MARCH, "100.00");
        spend("Food", "100.01", "2025-03-05");
        spend("Transport", "99.99", "2025-03-05");

        listBudgets(alice, "?month=" + MARCH)
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.categoryName=='Food')].exceeded").value(true))
                .andExpect(jsonPath("$[?(@.categoryName=='Food')].remaining").value(-0.01))
                .andExpect(jsonPath("$[?(@.categoryName=='Transport')].exceeded").value(false));
        listBudgets(alice, "?month=" + MARCH + "&exceededOnly=true")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].categoryName").value("Food"));
    }

    @Test
    void listingIsPerMonthAndSortedByCategoryName() throws Exception {
        createdBudget(alice, transport, MARCH, "10.00");
        createdBudget(alice, food, MARCH, "10.00");
        createdBudget(alice, food, "2025-04", "10.00");

        listBudgets(alice, "?month=" + MARCH)
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].categoryName").value("Food"))
                .andExpect(jsonPath("$[1].categoryName").value("Transport"));
        listBudgets(alice, "?month=2025-04").andExpect(jsonPath("$", hasSize(1)));
        listBudgets(alice, "?month=2025-05").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void updatingTheAmountRecomputesUtilisation() throws Exception {
        UUID id = createdBudget(alice, food, MARCH, "100.00");
        spend("Food", "80.00", "2025-03-05");

        mockMvc.perform(put("/api/v1/budgets/" + id)
                        .header(HttpHeaders.AUTHORIZATION, alice.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":50.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(50.00))
                .andExpect(jsonPath("$.utilisationPercent").value(160.0))
                .andExpect(jsonPath("$.exceeded").value(true));
    }

    @Test
    void budgetsOfOtherUsersAreInvisibleAndUntouchable() throws Exception {
        UUID id = createdBudget(alice, food, MARCH, "100.00");

        listBudgets(bob, "?month=" + MARCH).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(put("/api/v1/budgets/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bob.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1.00}"))
                .andExpect(status().isNotFound());

        listBudgets(alice, "?month=" + MARCH).andExpect(jsonPath("$[0].amount").value(100.00));
    }

    @Test
    void aCategoryWithABudgetCannotBeDeleted() throws Exception {
        createdBudget(alice, food, MARCH, "100.00");

        mockMvc.perform(delete("/api/v1/categories/" + food).header(HttpHeaders.AUTHORIZATION, alice.authorization()))
                .andExpect(status().isConflict());
    }
}
