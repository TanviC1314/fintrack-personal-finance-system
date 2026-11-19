package com.fintrack.account;

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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fintrack.support.PostgresTestContainerConfig;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainerConfig.class)
class AccountApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private Session alice;
    private Session bob;

    @BeforeEach
    void registerUsers() throws Exception {
        TestUsers users = new TestUsers(mockMvc, objectMapper);
        alice = users.registerAndLogin("Alice");
        bob = users.registerAndLogin("Bob");
    }

    private ResultActions createAccount(Session as, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/accounts")
                .header(HttpHeaders.AUTHORIZATION, as.authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private String accountJson(String name, String type, String currency, String openingBalance) {
        return """
                {"name":"%s","type":"%s","currency":"%s","openingBalance":%s}
                """.formatted(name, type, currency, openingBalance);
    }

    /** Creates an account and returns its id. */
    private UUID createdAccountId(Session as, String name, String openingBalance) throws Exception {
        String body = createAccount(as, accountJson(name, "CURRENT", "EUR", openingBalance))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private ResultActions getAccount(Session as, UUID id) throws Exception {
        return mockMvc.perform(get("/api/v1/accounts/" + id).header(HttpHeaders.AUTHORIZATION, as.authorization()));
    }

    // ---- create ----

    @Test
    void createReturns201WithLocationAndStartsWithOpeningBalanceAsCurrentBalance() throws Exception {
        createAccount(alice, accountJson("Everyday", "CURRENT", "EUR", "250.50"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.startsWith("/api/v1/accounts/")))
                .andExpect(jsonPath("$.name").value("Everyday"))
                .andExpect(jsonPath("$.type").value("CURRENT"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.openingBalance").value(250.50))
                .andExpect(jsonPath("$.currentBalance").value(250.50))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.userId").doesNotExist());
    }

    @Test
    void openingBalanceDefaultsToZero() throws Exception {
        createAccount(alice, "{\"name\":\"Wallet\",\"type\":\"CASH\",\"currency\":\"EUR\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentBalance").value(0));
    }

    @Test
    void createdAccountCanBeReadBack() throws Exception {
        UUID id = createdAccountId(alice, "Rainy day", "10.00");

        getAccount(alice, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Rainy day"));
    }

    // ---- validation ----

    @Test
    void invalidFieldsAreReportedTogether() throws Exception {
        createAccount(alice, "{\"name\":\"  \",\"currency\":\"ABC\",\"openingBalance\":-5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='name')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='type')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='currency')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='openingBalance')]").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.123", "1234567890123456789.00"})
    void openingBalanceMustFitMoneyPrecision(String balance) throws Exception {
        createAccount(alice, accountJson("Precise", "CURRENT", "EUR", balance))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='openingBalance')]").exists());
    }

    @Test
    void unknownAccountTypeIsABadRequestNotAServerError() throws Exception {
        createAccount(alice, accountJson("Odd", "CHECKING", "EUR", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nameLongerThanTheColumnIsRejected() throws Exception {
        createAccount(alice, accountJson("x".repeat(101), "CASH", "EUR", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='name')]").exists());
    }

    @Test
    void malformedPathIdIsABadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/not-a-uuid").header(HttpHeaders.AUTHORIZATION, alice.authorization()))
                .andExpect(status().isBadRequest());
    }

    // ---- name uniqueness ----

    @Test
    void duplicateActiveNameIsAConflictEvenWithDifferentCase() throws Exception {
        createdAccountId(alice, "Savings", "0");

        createAccount(alice, accountJson("SAVINGS", "SAVINGS", "EUR", "0"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    @Test
    void twoUsersMayUseTheSameAccountName() throws Exception {
        createdAccountId(alice, "Savings", "0");

        createAccount(bob, accountJson("Savings", "SAVINGS", "EUR", "0")).andExpect(status().isCreated());
    }

    // ---- listing and data isolation ----

    @Test
    void listContainsOnlyTheCallersAccounts() throws Exception {
        createdAccountId(alice, "Alice one", "0");
        createdAccountId(alice, "Alice two", "0");
        createdAccountId(bob, "Bob one", "0");

        mockMvc.perform(get("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, alice.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Alice one"))
                .andExpect(jsonPath("$[1].name").value("Alice two"));
    }

    @Test
    void anotherUsersAccountIsNotFoundForRead() throws Exception {
        UUID alicesAccount = createdAccountId(alice, "Private", "100.00");

        getAccount(bob, alicesAccount).andExpect(status().isNotFound());
    }

    @Test
    void anotherUsersAccountCannotBeRenamedOrArchivedAndStaysUntouched() throws Exception {
        UUID alicesAccount = createdAccountId(alice, "Private", "0");

        mockMvc.perform(put("/api/v1/accounts/" + alicesAccount)
                        .header(HttpHeaders.AUTHORIZATION, bob.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hijacked\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/accounts/" + alicesAccount)
                        .header(HttpHeaders.AUTHORIZATION, bob.authorization()))
                .andExpect(status().isNotFound());

        getAccount(alice, alicesAccount)
                .andExpect(jsonPath("$.name").value("Private"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void nonexistentAccountLooksIdenticalToAnotherUsersAccount() throws Exception {
        UUID alicesAccount = createdAccountId(alice, "Private", "0");

        String otherUsers = getAccount(bob, alicesAccount).andReturn().getResponse().getContentAsString();
        String missing = getAccount(bob, UUID.randomUUID()).andReturn().getResponse().getContentAsString();

        // Everything except "instance" (the echoed request path) must match, so existence is not revealed.
        assertThat(withoutInstance(otherUsers)).isEqualTo(withoutInstance(missing));
    }

    private JsonNode withoutInstance(String problemJson) throws Exception {
        ObjectNode problem = (ObjectNode) objectMapper.readTree(problemJson);
        problem.remove("instance");
        return problem;
    }

    // ---- authentication ----

    @Test
    void everyAccountEndpointRequiresAuthentication() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/accounts")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/accounts/" + id)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content(accountJson("X", "CASH", "EUR", "0"))).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/accounts/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/accounts/" + id)).andExpect(status().isUnauthorized());
    }

    // ---- rename ----

    @Test
    void renameUpdatesTheName() throws Exception {
        UUID id = createdAccountId(alice, "Old", "5.00");

        mockMvc.perform(put("/api/v1/accounts/" + id)
                        .header(HttpHeaders.AUTHORIZATION, alice.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New"))
                .andExpect(jsonPath("$.currentBalance").value(5.00));
    }

    @Test
    void renameToAnotherActiveAccountsNameIsAConflict() throws Exception {
        createdAccountId(alice, "Taken", "0");
        UUID id = createdAccountId(alice, "Other", "0");

        mockMvc.perform(put("/api/v1/accounts/" + id)
                        .header(HttpHeaders.AUTHORIZATION, alice.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"taken\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void renameWithBlankNameIsRejected() throws Exception {
        UUID id = createdAccountId(alice, "Named", "0");

        mockMvc.perform(put("/api/v1/accounts/" + id)
                        .header(HttpHeaders.AUTHORIZATION, alice.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---- archive ----

    @Test
    void emptyAccountCanBeArchivedAndThenIsHiddenFromTheDefaultList() throws Exception {
        UUID id = createdAccountId(alice, "Closing", "0");

        mockMvc.perform(delete("/api/v1/accounts/" + id).header(HttpHeaders.AUTHORIZATION, alice.authorization()))
                .andExpect(status().isNoContent());

        getAccount(alice, id).andExpect(jsonPath("$.status").value("ARCHIVED"));
        mockMvc.perform(get("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, alice.authorization()))
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/v1/accounts?includeArchived=true")
                        .header(HttpHeaders.AUTHORIZATION, alice.authorization()))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void accountHoldingMoneyCannotBeArchived() throws Exception {
        UUID id = createdAccountId(alice, "Funded", "0.01");

        mockMvc.perform(delete("/api/v1/accounts/" + id).header(HttpHeaders.AUTHORIZATION, alice.authorization()))
                .andExpect(status().isConflict());

        getAccount(alice, id).andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void archivingTwiceSucceedsBothTimes() throws Exception {
        UUID id = createdAccountId(alice, "Closing", "0");

        mockMvc.perform(delete("/api/v1/accounts/" + id).header(HttpHeaders.AUTHORIZATION, alice.authorization()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/accounts/" + id).header(HttpHeaders.AUTHORIZATION, alice.authorization()))
                .andExpect(status().isNoContent());
    }

    @Test
    void archivedAccountCannotBeRenamedButItsNameCanBeReused() throws Exception {
        UUID id = createdAccountId(alice, "Savings", "0");
        mockMvc.perform(delete("/api/v1/accounts/" + id).header(HttpHeaders.AUTHORIZATION, alice.authorization()))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/api/v1/accounts/" + id)
                        .header(HttpHeaders.AUTHORIZATION, alice.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isConflict());
        createAccount(alice, accountJson("Savings", "SAVINGS", "EUR", "0")).andExpect(status().isCreated());
    }
}
