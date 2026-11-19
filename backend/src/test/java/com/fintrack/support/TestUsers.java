package com.fintrack.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Registers and logs in throwaway users through the real API so tests can call protected endpoints. */
public class TestUsers {

    public static final String PASSWORD = "s3curePass";

    /** A logged-in user: their id and the Authorization header value to send. */
    public record Session(UUID userId, String authorization) {
    }

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public TestUsers(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    /** Creates a user with a unique email (tests share one database) and returns their session. */
    public Session registerAndLogin(String name) throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";

        String registerBody = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("name", name, "email", email, "password", PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        UUID userId = UUID.fromString(objectMapper.readTree(registerBody).get("id").asText());

        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        JsonNode login = objectMapper.readTree(loginBody);
        return new Session(userId, "Bearer " + login.get("accessToken").asText());
    }
}
