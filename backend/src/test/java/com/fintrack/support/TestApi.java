package com.fintrack.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.support.TestUsers.Session;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Small helpers that drive the real HTTP API so each test reads as a story rather than as plumbing. */
public class TestApi {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    // "user/name" -> category id, so tests can keep saying category("Food") many times.
    private final Map<String, UUID> categories = new ConcurrentHashMap<>();

    public TestApi(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    public UUID createAccount(Session as, String name, String openingBalance) throws Exception {
        String body = mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, as.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","type":"CURRENT","currency":"EUR","openingBalance":%s}
                                """.formatted(name, openingBalance)))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    /** Returns the id of the caller's category with this name, creating it the first time it is asked for. */
    public UUID categoryId(Session as, String name) {
        return categories.computeIfAbsent(as.userId() + "/" + name.toLowerCase(), key -> {
            try {
                String body = mockMvc.perform(post("/api/v1/categories")
                                .header(HttpHeaders.AUTHORIZATION, as.authorization())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"" + name + "\"}"))
                        .andReturn().getResponse().getContentAsString();
                return UUID.fromString(objectMapper.readTree(body).get("id").asText());
            } catch (Exception e) {
                throw new IllegalStateException("Could not create category " + name, e);
            }
        });
    }

    /** @param category a category name (created if needed) or null for an uncategorised transaction */
    public ResultActions postTransaction(Session as, UUID accountId, String type, String amount,
                                         String category, String date) throws Exception {
        String categoryJson = category == null ? "null" : "\"" + categoryId(as, category) + "\"";
        return postTransactionJson(as, """
                {"accountId":"%s","type":"%s","amount":%s,"categoryId":%s,"description":"test","transactionDate":"%s"}
                """.formatted(accountId, type, amount, categoryJson, date));
    }

    public ResultActions postTransactionJson(Session as, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/transactions")
                .header(HttpHeaders.AUTHORIZATION, as.authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    /** Creates a transaction that is expected to succeed and returns its id. */
    public UUID createTransaction(Session as, UUID accountId, String type, String amount,
                                  String category, String date) throws Exception {
        var response = postTransaction(as, accountId, type, amount, category, date).andReturn().getResponse();
        if (response.getStatus() != 201) {
            throw new IllegalStateException("Expected 201 but got " + response.getStatus() + ": "
                    + response.getContentAsString());
        }
        return UUID.fromString(objectMapper.readTree(response.getContentAsString()).get("id").asText());
    }

    public BigDecimal balanceOf(Session as, UUID accountId) throws Exception {
        String body = mockMvc.perform(get("/api/v1/accounts/" + accountId)
                        .header(HttpHeaders.AUTHORIZATION, as.authorization()))
                .andReturn().getResponse().getContentAsString();
        return new BigDecimal(objectMapper.readTree(body).get("currentBalance").asText());
    }
}
