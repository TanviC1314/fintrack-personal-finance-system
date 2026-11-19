package com.fintrack.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.support.PostgresTestContainerConfig;
import com.fintrack.support.TestApi;
import com.fintrack.support.TestUsers;
import com.fintrack.support.TestUsers.Session;
import java.time.LocalDate;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainerConfig.class)
class TransferApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private TestApi api;
    private Session alice;
    private Session bob;
    private UUID current;   // 100.00
    private UUID savings;   // 20.00
    private UUID bobsAccount;

    @BeforeEach
    void setUp() throws Exception {
        api = new TestApi(mockMvc, objectMapper);
        TestUsers users = new TestUsers(mockMvc, objectMapper);
        alice = users.registerAndLogin("Alice");
        bob = users.registerAndLogin("Bob");
        current = api.createAccount(alice, "Current", "100.00");
        savings = api.createAccount(alice, "Savings", "20.00");
        bobsAccount = api.createAccount(bob, "Bobs", "500.00");
    }

    private static String newKey() {
        return "key-" + UUID.randomUUID();
    }

    private ResultActions transfer(Session as, String key, UUID from, UUID to, String amount) throws Exception {
        return transferJson(as, key, """
                {"sourceAccountId":"%s","destinationAccountId":"%s","amount":%s}
                """.formatted(from, to, amount));
    }

    private ResultActions transferJson(Session as, String key, String json) throws Exception {
        var request = post("/api/v1/transfers")
                .header(HttpHeaders.AUTHORIZATION, as.authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mockMvc.perform(request);
    }

    private ResultActions listTransfers(Session as, String query) throws Exception {
        return mockMvc.perform(get("/api/v1/transfers" + query).header(HttpHeaders.AUTHORIZATION, as.authorization()));
    }

    private void assertBalances(String currentBalance, String savingsBalance) throws Exception {
        assertThat(api.balanceOf(alice, current)).isEqualByComparingTo(currentBalance);
        assertThat(api.balanceOf(alice, savings)).isEqualByComparingTo(savingsBalance);
    }

    // ---- success ----

    @Test
    void transferMovesMoneyAndShowsBothSidesInTheTransactionHistory() throws Exception {
        transfer(alice, newKey(), current, savings, "40.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceAccountId").value(current.toString()))
                .andExpect(jsonPath("$.destinationAccountId").value(savings.toString()))
                .andExpect(jsonPath("$.amount").value(40.00));

        assertBalances("60.00", "60.00");
        mockMvc.perform(get("/api/v1/transactions?type=TRANSFER_OUT")
                        .header(HttpHeaders.AUTHORIZATION, alice.authorization()))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].accountId").value(current.toString()))
                .andExpect(jsonPath("$.content[0].description").value("Transfer to Savings"));
        mockMvc.perform(get("/api/v1/transactions?type=TRANSFER_IN")
                        .header(HttpHeaders.AUTHORIZATION, alice.authorization()))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].accountId").value(savings.toString()))
                .andExpect(jsonPath("$.content[0].description").value("Transfer from Current"));
    }

    @Test
    void wholeBalanceCanBeTransferred() throws Exception {
        transfer(alice, newKey(), current, savings, "100.00").andExpect(status().isCreated());

        assertBalances("0.00", "120.00");
    }

    // ---- idempotency ----

    @Test
    void repeatingARequestWithTheSameKeyMovesTheMoneyOnlyOnce() throws Exception {
        String key = newKey();

        transfer(alice, key, current, savings, "40.00").andExpect(status().isCreated());
        transfer(alice, key, current, savings, "40.00")
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"));
        transfer(alice, key, current, savings, "40.00").andExpect(status().isOk());

        assertBalances("60.00", "60.00");
        listTransfers(alice, "").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void replayReturnsTheSameTransfer() throws Exception {
        String key = newKey();
        String first = transfer(alice, key, current, savings, "40.00").andReturn().getResponse().getContentAsString();
        String second = transfer(alice, key, current, savings, "40.00").andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(second).get("id")).isEqualTo(objectMapper.readTree(first).get("id"));
    }

    @Test
    void reusingAKeyForADifferentTransferIsAConflictAndChangesNothing() throws Exception {
        String key = newKey();
        transfer(alice, key, current, savings, "40.00").andExpect(status().isCreated());

        transfer(alice, key, current, savings, "41.00").andExpect(status().isConflict());
        transfer(alice, key, savings, current, "40.00").andExpect(status().isConflict());

        assertBalances("60.00", "60.00");
    }

    @Test
    void sameKeyTextFromAnotherUserIsIndependent() throws Exception {
        String key = newKey();
        UUID bobsSecond = api.createAccount(bob, "Bobs two", "0");

        transfer(alice, key, current, savings, "10.00").andExpect(status().isCreated());
        transfer(bob, key, bobsAccount, bobsSecond, "10.00").andExpect(status().isCreated());

        assertThat(api.balanceOf(bob, bobsSecond)).isEqualByComparingTo("10.00");
    }

    @Test
    void theIdempotencyKeyHeaderIsRequired() throws Exception {
        transfer(alice, null, current, savings, "10.00").andExpect(status().isBadRequest());

        assertBalances("100.00", "20.00");
    }

    @Test
    void malformedIdempotencyKeysAreRejected() throws Exception {
        transfer(alice, "has space", current, savings, "10.00").andExpect(status().isBadRequest());
        transfer(alice, "k".repeat(101), current, savings, "10.00").andExpect(status().isBadRequest());

        assertBalances("100.00", "20.00");
    }

    // ---- business rules ----

    @Test
    void insufficientFundsIsRejectedAndNothingChanges() throws Exception {
        transfer(alice, newKey(), current, savings, "100.01").andExpect(status().isUnprocessableEntity());

        assertBalances("100.00", "20.00");
        listTransfers(alice, "").andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/v1/transactions?type=TRANSFER_IN")
                        .header(HttpHeaders.AUTHORIZATION, alice.authorization()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void transferToTheSameAccountIsRejected() throws Exception {
        transfer(alice, newKey(), current, current, "10.00").andExpect(status().isBadRequest());

        assertBalances("100.00", "20.00");
    }

    @Test
    void transferToAnotherUsersAccountIsNotFoundAndNothingChanges() throws Exception {
        transfer(alice, newKey(), current, bobsAccount, "10.00").andExpect(status().isNotFound());
        transfer(alice, newKey(), bobsAccount, current, "10.00").andExpect(status().isNotFound());
        transfer(bob, newKey(), current, bobsAccount, "10.00").andExpect(status().isNotFound());

        assertBalances("100.00", "20.00");
        assertThat(api.balanceOf(bob, bobsAccount)).isEqualByComparingTo("500.00");
    }

    @Test
    void unknownAccountIsNotFound() throws Exception {
        transfer(alice, newKey(), current, UUID.randomUUID(), "10.00").andExpect(status().isNotFound());
    }

    @Test
    void archivedAccountsCannotBeUsed() throws Exception {
        UUID empty = api.createAccount(alice, "Closing", "0");
        mockMvc.perform(delete("/api/v1/accounts/" + empty).header(HttpHeaders.AUTHORIZATION, alice.authorization()))
                .andExpect(status().isNoContent());

        transfer(alice, newKey(), current, empty, "10.00").andExpect(status().isConflict());

        assertBalances("100.00", "20.00");
    }

    @Test
    void accountsInDifferentCurrenciesCannotTransfer() throws Exception {
        String body = mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, alice.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Dollars\",\"type\":\"CASH\",\"currency\":\"USD\",\"openingBalance\":0}"))
                .andReturn().getResponse().getContentAsString();
        UUID dollars = UUID.fromString(objectMapper.readTree(body).get("id").asText());

        transfer(alice, newKey(), current, dollars, "10.00").andExpect(status().isUnprocessableEntity());

        assertBalances("100.00", "20.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "0.001", "12.345"})
    void invalidAmountsAreRejected(String amount) throws Exception {
        transfer(alice, newKey(), current, savings, amount).andExpect(status().isBadRequest());

        assertBalances("100.00", "20.00");
    }

    @Test
    void missingFieldsAreReported() throws Exception {
        transferJson(alice, newKey(), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='sourceAccountId')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='destinationAccountId')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='amount')]").exists());
    }

    @Test
    void futureDatesAreRejected() throws Exception {
        transferJson(alice, newKey(), """
                {"sourceAccountId":"%s","destinationAccountId":"%s","amount":10.00,"transferDate":"%s"}
                """.formatted(current, savings, LocalDate.now().plusDays(30)))
                .andExpect(status().isBadRequest());
    }

    // ---- transfer legs are managed through the transfer ----

    @Test
    void transferLegsCannotBeEditedOrReversedThroughTheTransactionApi() throws Exception {
        transfer(alice, newKey(), current, savings, "40.00").andExpect(status().isCreated());
        String page = mockMvc.perform(get("/api/v1/transactions?type=TRANSFER_OUT")
                        .header(HttpHeaders.AUTHORIZATION, alice.authorization()))
                .andReturn().getResponse().getContentAsString();
        JsonNode leg = objectMapper.readTree(page).get("content").get(0);
        String legId = leg.get("id").asText();

        mockMvc.perform(put("/api/v1/transactions/" + legId)
                        .header(HttpHeaders.AUTHORIZATION, alice.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1.00,\"transactionDate\":\"2025-01-01\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/v1/transactions/" + legId).header(HttpHeaders.AUTHORIZATION, alice.authorization()))
                .andExpect(status().isConflict());

        assertBalances("60.00", "60.00");
    }

    // ---- listing ----

    @Test
    void listShowsOnlyTheCallersTransfersNewestFirst() throws Exception {
        transfer(alice, newKey(), current, savings, "10.00").andExpect(status().isCreated());
        transfer(alice, newKey(), savings, current, "5.00").andExpect(status().isCreated());

        listTransfers(alice, "")
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].amount").value(5.00))
                .andExpect(jsonPath("$.content[1].amount").value(10.00));
        listTransfers(bob, "").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listCanBeFilteredByEitherAccountAndPaged() throws Exception {
        UUID third = api.createAccount(alice, "Third", "0");
        transfer(alice, newKey(), current, savings, "10.00").andExpect(status().isCreated());
        transfer(alice, newKey(), current, third, "10.00").andExpect(status().isCreated());

        listTransfers(alice, "?accountId=" + third).andExpect(jsonPath("$.totalElements").value(1));
        listTransfers(alice, "?accountId=" + savings).andExpect(jsonPath("$.totalElements").value(1));
        listTransfers(alice, "?accountId=" + current).andExpect(jsonPath("$.totalElements").value(2));
        listTransfers(alice, "?size=1&page=1")
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void filteringByAnotherUsersAccountRevealsNothing() throws Exception {
        listTransfers(alice, "?accountId=" + bobsAccount).andExpect(jsonPath("$.totalElements").value(0));
    }

    // ---- authentication ----

    @Test
    void transferEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/transfers")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", newKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
