package com.fintrack.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.support.PostgresTestContainerConfig;
import com.fintrack.support.TestApi;
import com.fintrack.support.TestUsers;
import com.fintrack.support.TestUsers.Session;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/** The {@code q} parameter searches the description, case-insensitively, within the caller's own data. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainerConfig.class)
class TransactionTextSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private Session alice;
    private Session bob;
    private UUID coffee;
    private UUID groceries;
    private UUID discount;
    private UUID underscore;
    private UUID noDescription;

    @BeforeEach
    void setUp() throws Exception {
        TestApi api = new TestApi(mockMvc, objectMapper);
        TestUsers users = new TestUsers(mockMvc, objectMapper);
        alice = users.registerAndLogin("Alice");
        bob = users.registerAndLogin("Bob");
        UUID account = api.createAccount(alice, "Main", "500.00");
        coffee = add(api, alice, account, "4.50", "\"Morning Coffee\"");
        groceries = add(api, alice, account, "30.00", "\"Weekly groceries\"");
        discount = add(api, alice, account, "9.00", "\"50% discount lunch\"");
        underscore = add(api, alice, account, "2.00", "\"snack_bar\"");
        noDescription = add(api, alice, account, "1.00", "null");
        UUID bobAccount = api.createAccount(bob, "Bob main", "100.00");
        add(api, bob, bobAccount, "3.00", "\"Coffee with Bob\"");
    }

    private UUID add(TestApi api, Session owner, UUID account, String amount, String descriptionJson) throws Exception {
        String json = """
                {"accountId":"%s","type":"EXPENSE","amount":%s,"categoryId":null,"description":%s,"transactionDate":"2025-03-01"}
                """.formatted(account, amount, descriptionJson);
        var response = api.postTransactionJson(owner, json)
                .andExpect(status().isCreated()).andReturn().getResponse();
        return UUID.fromString(objectMapper.readTree(response.getContentAsString()).get("id").asText());
    }

    /** Builds query parameters from "?a=b&c=d" without URL-template encoding, so % and spaces reach the API as typed. */
    private static org.springframework.util.MultiValueMap<String, String> parse(String query) {
        var params = new org.springframework.util.LinkedMultiValueMap<String, String>();
        for (String pair : query.substring(1).split("&")) {
            int eq = pair.indexOf('=');
            params.add(pair.substring(0, eq), pair.substring(eq + 1));
        }
        return params;
    }

    private List<String> search(Session as, String query) throws Exception {
        String body = mockMvc.perform(get("/api/v1/transactions").params(parse(query)).header(HttpHeaders.AUTHORIZATION, as.authorization()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode content = objectMapper.readTree(body).get("content");
        List<String> ids = new ArrayList<>();
        content.forEach(node -> ids.add(node.get("id").asText()));
        return ids;
    }

    @Test
    void matchesPartOfTheDescriptionIgnoringCase() throws Exception {
        assertThat(search(alice, "?q=COFFEE")).containsExactly(coffee.toString());
        assertThat(search(alice, "?q=grocer")).containsExactly(groceries.toString());
    }

    @Test
    void blankTextDoesNotFilter() throws Exception {
        assertThat(search(alice, "?q=  ")).hasSize(5);
        assertThat(search(alice, "?q=")).hasSize(5);
    }

    @Test
    void percentAndUnderscoreAreMatchedLiterally() throws Exception {
        assertThat(search(alice, "?q=50%")).containsExactly(discount.toString());
        assertThat(search(alice, "?q=_")).containsExactly(underscore.toString());
        assertThat(search(alice, "?q=%")).containsExactly(discount.toString());
    }

    @Test
    void transactionsWithoutADescriptionNeverMatch() throws Exception {
        assertThat(search(alice, "?q=a")).doesNotContain(noDescription.toString());
    }

    @Test
    void searchNeverReturnsAnotherUsersTransactions() throws Exception {
        assertThat(search(alice, "?q=bob")).isEmpty();
        assertThat(search(bob, "?q=coffee")).hasSize(1).doesNotContain(coffee.toString());
    }

    @Test
    void combinesWithOtherFilters() throws Exception {
        assertThat(search(alice, "?q=coffee&type=INCOME")).isEmpty();
        assertThat(search(alice, "?q=coffee&type=EXPENSE")).containsExactly(coffee.toString());
    }
}
