package com.fintrack.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.support.PostgresTestContainerConfig;
import com.fintrack.support.TestApi;
import com.fintrack.support.TestUsers;
import com.fintrack.support.TestUsers.Session;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Fixture for Alice (account A holds 1000.00 and B holds 100.00):
 * <pre>
 *   salary   INCOME  1000.00  A  Salary     2025-01-05
 *   grocery  EXPENSE   50.00  A  Food       2025-01-10
 *   bus      EXPENSE   20.00  A  Transport  2025-02-01
 *   lunch    EXPENSE   30.00  B  Food       2025-02-15
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainerConfig.class)
class TransactionSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private TestApi api;
    private Session alice;
    private Session bob;
    private UUID accountA;
    private UUID accountB;
    private UUID salary;
    private UUID grocery;
    private UUID bus;
    private UUID lunch;

    @BeforeEach
    void setUp() throws Exception {
        api = new TestApi(mockMvc, objectMapper);
        TestUsers users = new TestUsers(mockMvc, objectMapper);
        alice = users.registerAndLogin("Alice");
        bob = users.registerAndLogin("Bob");
        accountA = api.createAccount(alice, "A", "0");
        accountB = api.createAccount(alice, "B", "100.00");
        salary = api.createTransaction(alice, accountA, "INCOME", "1000.00", "Salary", "2025-01-05");
        grocery = api.createTransaction(alice, accountA, "EXPENSE", "50.00", "Food", "2025-01-10");
        bus = api.createTransaction(alice, accountA, "EXPENSE", "20.00", "Transport", "2025-02-01");
        lunch = api.createTransaction(alice, accountB, "EXPENSE", "30.00", "Food", "2025-02-15");
    }

    private ResultActions search(Session as, String query) throws Exception {
        return mockMvc.perform(get("/api/v1/transactions" + query).header(HttpHeaders.AUTHORIZATION, as.authorization()));
    }

    private List<String> idsOf(ResultActions result) throws Exception {
        JsonNode content = objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("content");
        List<String> ids = new ArrayList<>();
        content.forEach(node -> ids.add(node.get("id").asText()));
        return ids;
    }

    private List<String> ids(UUID... uuids) {
        return java.util.Arrays.stream(uuids).map(UUID::toString).toList();
    }

    // ---- filters ----

    @Test
    void withoutFiltersAllOfTheCallersTransactionsAreReturnedNewestDateFirst() throws Exception {
        assertThat(idsOf(search(alice, ""))).containsExactlyElementsOf(ids(lunch, bus, grocery, salary));
    }

    @Test
    void filterByAccount() throws Exception {
        assertThat(idsOf(search(alice, "?accountId=" + accountB))).containsExactly(lunch.toString());
    }

    @Test
    void filterByType() throws Exception {
        assertThat(idsOf(search(alice, "?type=INCOME"))).containsExactly(salary.toString());
        assertThat(idsOf(search(alice, "?type=EXPENSE"))).containsExactlyElementsOf(ids(lunch, bus, grocery));
    }

    @Test
    void filterByCategory() throws Exception {
        UUID food = api.categoryId(alice, "Food");

        assertThat(idsOf(search(alice, "?categoryId=" + food))).containsExactlyElementsOf(ids(lunch, grocery));
    }

    @Test
    void dateRangeIsInclusiveAtBothEnds() throws Exception {
        assertThat(idsOf(search(alice, "?from=2025-01-10&to=2025-02-01"))).containsExactlyElementsOf(ids(bus, grocery));
        assertThat(idsOf(search(alice, "?from=2025-02-02"))).containsExactly(lunch.toString());
        assertThat(idsOf(search(alice, "?to=2025-01-09"))).containsExactly(salary.toString());
    }

    @Test
    void filtersCombineWithAnd() throws Exception {
        assertThat(idsOf(search(alice, "?accountId=" + accountA + "&categoryId=" + api.categoryId(alice, "Food")
                + "&type=EXPENSE&from=2025-01-01")))
                .containsExactly(grocery.toString());
    }

    @Test
    void aFilterThatMatchesNothingReturnsAnEmptyPage() throws Exception {
        search(alice, "?categoryId=" + UUID.randomUUID())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void reversedTransactionsAreHiddenUnlessRequested() throws Exception {
        mockMvc.perform(delete("/api/v1/transactions/" + bus).header(HttpHeaders.AUTHORIZATION, alice.authorization()))
                .andExpect(status().isNoContent());

        assertThat(idsOf(search(alice, ""))).doesNotContain(bus.toString());
        assertThat(idsOf(search(alice, "?includeReversed=true"))).contains(bus.toString());
    }

    @Test
    void invalidFilterValuesAreBadRequests() throws Exception {
        search(alice, "?from=2025-03-01&to=2025-01-01").andExpect(status().isBadRequest());
        search(alice, "?from=yesterday").andExpect(status().isBadRequest());
        search(alice, "?type=GIFT").andExpect(status().isBadRequest());
        search(alice, "?accountId=not-a-uuid").andExpect(status().isBadRequest());
    }

    // ---- sorting ----

    @Test
    void sortByAmountAscendingAndDescending() throws Exception {
        assertThat(idsOf(search(alice, "?sort=amount,asc"))).containsExactlyElementsOf(ids(bus, lunch, grocery, salary));
        assertThat(idsOf(search(alice, "?sort=amount,desc"))).containsExactlyElementsOf(ids(salary, grocery, lunch, bus));
    }

    @Test
    void sortingByAnythingOutsideTheWhitelistIsRejected() throws Exception {
        search(alice, "?sort=account.userId,asc").andExpect(status().isBadRequest());
        search(alice, "?sort=reversedAt,asc").andExpect(status().isBadRequest());
        search(alice, "?sort=nonsense").andExpect(status().isBadRequest());
    }

    // ---- pagination ----

    @Test
    void pagesReportTotalsAndSplitTheResults() throws Exception {
        search(alice, "?size=3&page=0")
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2));
        search(alice, "?size=3&page=1").andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    void walkingEveryPageVisitsEachTransactionExactlyOnce() throws Exception {
        Set<String> seen = new HashSet<>();
        int total = 0;
        for (int page = 0; page < 4; page++) {
            List<String> pageIds = idsOf(search(alice, "?size=1&page=" + page));
            total += pageIds.size();
            seen.addAll(pageIds);
        }

        assertThat(total).isEqualTo(4);
        assertThat(seen).hasSize(4);
    }

    @Test
    void pageBeyondTheEndIsEmptyNotAnError() throws Exception {
        search(alice, "?size=10&page=5")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void pageSizeIsCapped() throws Exception {
        search(alice, "?size=100000").andExpect(jsonPath("$.size").value(100));
    }

    // ---- isolation ----

    @Test
    void otherUsersNeverSeeTheCallersTransactions() throws Exception {
        search(bob, "").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void filteringByAnotherUsersAccountRevealsNothing() throws Exception {
        search(bob, "?accountId=" + accountA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }
}
