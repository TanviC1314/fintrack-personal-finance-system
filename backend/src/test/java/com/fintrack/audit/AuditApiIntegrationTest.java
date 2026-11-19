package com.fintrack.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainerConfig.class)
class AuditApiIntegrationTest {

    private static final String DAY = "2025-03-10";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestApi api;
    private Session alice;
    private Session bob;

    @BeforeEach
    void setUp() throws Exception {
        api = new TestApi(mockMvc, objectMapper);
        TestUsers users = new TestUsers(mockMvc, objectMapper);
        alice = users.registerAndLogin("Alice");
        bob = users.registerAndLogin("Bob");
    }

    private ResultActions auditLog(Session as, String query) throws Exception {
        return mockMvc.perform(get("/api/v1/audit-logs" + query).header(HttpHeaders.AUTHORIZATION, as.authorization()));
    }

    @Test
    void accountLifecycleIsRecordedNewestFirst() throws Exception {
        UUID account = api.createAccount(alice, "Old name", "0");
        mockMvc.perform(put("/api/v1/accounts/" + account)
                .header(HttpHeaders.AUTHORIZATION, alice.authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New name\"}")).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/accounts/" + account)
                .header(HttpHeaders.AUTHORIZATION, alice.authorization())).andExpect(status().isNoContent());

        auditLog(alice, "?entityId=" + account)
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].action").value("ARCHIVED"))
                .andExpect(jsonPath("$.content[1].action").value("RENAMED"))
                .andExpect(jsonPath("$.content[1].details.from").value("Old name"))
                .andExpect(jsonPath("$.content[1].details.to").value("New name"))
                .andExpect(jsonPath("$.content[2].action").value("CREATED"))
                .andExpect(jsonPath("$.content[2].entityType").value("ACCOUNT"))
                .andExpect(jsonPath("$.content[2].details.openingBalance").value(0));
    }

    @Test
    void transactionCreateEditAndReverseAreRecordedWithAmounts() throws Exception {
        UUID account = api.createAccount(alice, "Main", "100.00");
        UUID tx = api.createTransaction(alice, account, "EXPENSE", "20.00", "Food", DAY);
        mockMvc.perform(put("/api/v1/transactions/" + tx)
                .header(HttpHeaders.AUTHORIZATION, alice.authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":35.50,\"transactionDate\":\"" + DAY + "\"}")).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/transactions/" + tx)
                .header(HttpHeaders.AUTHORIZATION, alice.authorization())).andExpect(status().isNoContent());

        auditLog(alice, "?entityId=" + tx)
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].action").value("REVERSED"))
                .andExpect(jsonPath("$.content[0].details.amount").value(35.50))
                .andExpect(jsonPath("$.content[1].action").value("UPDATED"))
                .andExpect(jsonPath("$.content[1].details.previousAmount").value(20.00))
                .andExpect(jsonPath("$.content[1].details.newAmount").value(35.50))
                .andExpect(jsonPath("$.content[2].action").value("CREATED"))
                .andExpect(jsonPath("$.content[2].details.type").value("EXPENSE"));
    }

    @Test
    void aTransferIsRecordedOnceEvenWhenTheRequestIsRepeated() throws Exception {
        UUID from = api.createAccount(alice, "From", "50.00");
        UUID to = api.createAccount(alice, "To", "0");
        String key = "audit-" + UUID.randomUUID();
        String body = "{\"sourceAccountId\":\"%s\",\"destinationAccountId\":\"%s\",\"amount\":10.00}".formatted(from, to);

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/transfers")
                    .header(HttpHeaders.AUTHORIZATION, alice.authorization())
                    .header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body));
        }

        auditLog(alice, "?entityType=TRANSFER")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].action").value("CREATED"))
                .andExpect(jsonPath("$.content[0].details.sourceAccountId").value(from.toString()))
                .andExpect(jsonPath("$.content[0].details.amount").value(10.00));
    }

    @Test
    void rejectedOperationsLeaveNoAuditEntry() throws Exception {
        UUID account = api.createAccount(alice, "Small", "10.00");
        int before = countEntriesFor(account);

        api.postTransaction(alice, account, "EXPENSE", "10.01", null, DAY).andExpect(status().isUnprocessableEntity());
        api.postTransaction(alice, account, "EXPENSE", "-1", null, DAY).andExpect(status().isBadRequest());

        assertThat(countEntriesFor(account)).isEqualTo(before);
        auditLog(alice, "?entityType=TRANSACTION").andExpect(jsonPath("$.totalElements").value(0));
    }

    private int countEntriesFor(UUID entityId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM audit_log WHERE entity_id = ?", Integer.class, entityId);
    }

    @Test
    void filtersNarrowTheHistory() throws Exception {
        UUID account = api.createAccount(alice, "Main", "100.00");
        api.createTransaction(alice, account, "INCOME", "5.00", null, DAY);

        auditLog(alice, "?entityType=ACCOUNT").andExpect(jsonPath("$.totalElements").value(1));
        auditLog(alice, "?entityType=TRANSACTION").andExpect(jsonPath("$.totalElements").value(1));
        auditLog(alice, "").andExpect(jsonPath("$.totalElements").value(2));
        auditLog(alice, "?size=1").andExpect(jsonPath("$.content", hasSize(1))).andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void usersOnlySeeTheirOwnHistory() throws Exception {
        UUID account = api.createAccount(alice, "Private", "100.00");

        auditLog(bob, "").andExpect(jsonPath("$.totalElements").value(0));
        auditLog(bob, "?entityId=" + account).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void auditEntriesCannotBeChangedOrRemovedEvenWithDirectDatabaseAccess() throws Exception {
        UUID account = api.createAccount(alice, "Main", "1.00");
        assertThat(countEntriesFor(account)).isEqualTo(1);

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE audit_log SET action = 'ARCHIVED' WHERE entity_id = ?", account))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM audit_log WHERE entity_id = ?", account))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");

        assertThat(countEntriesFor(account)).isEqualTo(1);
    }

    @Test
    void auditEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs")).andExpect(status().isUnauthorized());
    }
}
