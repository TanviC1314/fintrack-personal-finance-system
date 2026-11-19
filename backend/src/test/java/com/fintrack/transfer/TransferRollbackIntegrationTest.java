package com.fintrack.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.audit.entity.AuditEntityType;
import com.fintrack.audit.service.AuditService;
import com.fintrack.support.PostgresTestContainerConfig;
import com.fintrack.support.TestApi;
import com.fintrack.support.TestUsers;
import com.fintrack.support.TestUsers.Session;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves a transfer is all-or-nothing. The failure is injected at the very last step, after both
 * balances changed and the transfer row and both ledger legs were already written, which is the
 * worst moment for it to happen.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainerConfig.class)
class TransferRollbackIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoSpyBean
    private AuditService auditService;

    private TestApi api;
    private Session alice;
    private UUID source;
    private UUID destination;

    @BeforeEach
    void setUp() throws Exception {
        api = new TestApi(mockMvc, objectMapper);
        alice = new TestUsers(mockMvc, objectMapper).registerAndLogin("Alice");
        source = api.createAccount(alice, "Source", "100.00");
        destination = api.createAccount(alice, "Destination", "20.00");
    }

    /**
     * The injected bean is Spring's transactional proxy; stubbing has to target the object behind it,
     * otherwise the stubbing call itself would pass through the transaction interceptor.
     */
    private AuditService auditTarget() {
        return AopTestUtils.getUltimateTargetObject(auditService);
    }

    private void failTransferAuditEntries(String message) {
        doThrow(new IllegalStateException(message))
                .when(auditTarget()).record(any(), eq(AuditEntityType.TRANSFER), any(), any(), any());
    }

    private void stopFailing() {
        Mockito.reset(auditTarget());
    }

    private int count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    private int status(String key, String amount) throws Exception {
        return mockMvc.perform(post("/api/v1/transfers")
                        .header(HttpHeaders.AUTHORIZATION, alice.authorization())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\":\"%s\",\"destinationAccountId\":\"%s\",\"amount\":%s}"
                                .formatted(source, destination, amount)))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void aFailureAfterEverythingWasWrittenRollsBackBalancesTransferLedgerAndAudit() throws Exception {
        failTransferAuditEntries("simulated failure at the last step");

        int result = status("rollback-" + UUID.randomUUID(), "40.00");

        assertThat(result).isEqualTo(500);
        assertThat(api.balanceOf(alice, source)).isEqualByComparingTo("100.00");
        assertThat(api.balanceOf(alice, destination)).isEqualByComparingTo("20.00");
        assertThat(count("SELECT count(*) FROM transfers WHERE source_account_id = ?", source)).isZero();
        assertThat(count("SELECT count(*) FROM transactions WHERE account_id IN (?, ?)", source, destination)).isZero();
        assertThat(count("SELECT count(*) FROM audit_log WHERE entity_type = 'TRANSFER' AND user_id = ?",
                alice.userId())).isZero();
    }

    @Test
    void afterAFailedAttemptTheSameKeyCanBeRetriedAndSucceedsExactlyOnce() throws Exception {
        String key = "retry-" + UUID.randomUUID();
        failTransferAuditEntries("simulated failure");
        assertThat(status(key, "40.00")).isEqualTo(500);

        stopFailing();
        assertThat(status(key, "40.00")).isEqualTo(201);
        assertThat(status(key, "40.00")).isEqualTo(200);

        assertThat(api.balanceOf(alice, source)).isEqualByComparingTo("60.00");
        assertThat(api.balanceOf(alice, destination)).isEqualByComparingTo("60.00");
        assertThat(count("SELECT count(*) FROM transfers WHERE source_account_id = ?", source)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM transactions WHERE account_id IN (?, ?)", source, destination))
                .isEqualTo(2);
    }

    @Test
    void aFailureDoesNotAffectTheNextUnrelatedTransfer() throws Exception {
        failTransferAuditEntries("simulated failure");
        assertThat(status("first-" + UUID.randomUUID(), "40.00")).isEqualTo(500);

        stopFailing();

        assertThat(status("second-" + UUID.randomUUID(), "10.00")).isEqualTo(201);
        assertThat(api.balanceOf(alice, source)).isEqualByComparingTo("90.00");
        assertThat(api.balanceOf(alice, destination)).isEqualByComparingTo("30.00");
    }
}
