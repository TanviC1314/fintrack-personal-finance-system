package com.fintrack.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.security.JwtProperties;
import com.fintrack.security.JwtService;
import com.fintrack.support.PostgresTestContainerConfig;
import com.fintrack.user.entity.User;
import com.fintrack.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainerConfig.class)
class AuthApiIntegrationTest {

    private static final String PASSWORD = "s3curePass";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtProperties jwtProperties;

    // Each test uses its own email because all tests share one database.
    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private MvcResult register(String name, String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        java.util.Map.of("name", name, "email", email, "password", password))))
                .andReturn();
    }

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", password))))
                .andReturn();
    }

    private String registerAndLogin(String name, String email) throws Exception {
        assertThat(register(name, email, PASSWORD).getResponse().getStatus()).isEqualTo(201);
        MvcResult loginResult = login(email, PASSWORD);
        assertThat(loginResult.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    @Test
    void registrationCreatesUserWithoutExposingThePasswordOrHash() throws Exception {
        String email = uniqueEmail();

        MvcResult result = register("Alice", email, PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("email").asText()).isEqualTo(email);
        assertThat(body.get("id").asText()).isNotBlank();
        assertThat(body.has("password")).isFalse();
        assertThat(body.has("passwordHash")).isFalse();

        User stored = userRepository.findByEmail(email).orElseThrow();
        assertThat(stored.getPasswordHash()).startsWith("$2").isNotEqualTo(PASSWORD);
    }

    @Test
    void registeringTheSameEmailTwiceIsRejectedEvenWithDifferentCase() throws Exception {
        String email = uniqueEmail();
        register("Alice", email, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("name", "Impostor", "email", email.toUpperCase(), "password", PASSWORD))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Email already registered"));
    }

    @Test
    void invalidRegistrationReturnsFieldLevelErrors() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors[?(@.field=='name')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='email')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='password')]").exists());
    }

    @Test
    void malformedJsonReturnsBadRequestNotServerError() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginTokenGrantsAccessToTheCallersOwnProfile() throws Exception {
        String email = uniqueEmail();
        String token = registerAndLogin("Alice", email);

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.name").value("Alice"));
    }

    @Test
    void eachUserOnlySeesTheirOwnProfile() throws Exception {
        String aliceEmail = uniqueEmail();
        String bobEmail = uniqueEmail();
        String aliceToken = registerAndLogin("Alice", aliceEmail);
        String bobToken = registerAndLogin("Bob", bobEmail);

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(jsonPath("$.email").value(aliceEmail));
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + bobToken))
                .andExpect(jsonPath("$.email").value(bobEmail));
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveIdenticalUnauthorizedResponses() throws Exception {
        String email = uniqueEmail();
        register("Alice", email, PASSWORD);

        MvcResult wrongPassword = login(email, "wrong-password1");
        MvcResult unknownEmail = login(uniqueEmail(), PASSWORD);

        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknownEmail.getResponse().getStatus()).isEqualTo(401);
        assertThat(wrongPassword.getResponse().getContentAsString())
                .isEqualTo(unknownEmail.getResponse().getContentAsString());
    }

    @Test
    void emailLoginIsCaseInsensitive() throws Exception {
        String email = uniqueEmail();
        register("Alice", email, PASSWORD);

        assertThat(login(email.toUpperCase(), PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void protectedEndpointWithoutTokenReturnsUnauthorizedProblem() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void anyUnmappedApiPathRequiresAuthenticationRatherThanLeakingWhatExists() throws Exception {
        mockMvc.perform(get("/api/v1/accounts")).andExpect(status().isUnauthorized());
    }

    @Test
    void garbageTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String email = uniqueEmail();
        register("Alice", email, PASSWORD);
        UUID userId = userRepository.findByEmail(email).orElseThrow().getId();
        JwtService issuedTwoHoursAgo = new JwtService(jwtProperties,
                Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC));

        String expired = issuedTwoHoursAgo.generateToken(userId, email);

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithADifferentSecretIsRejected() throws Exception {
        String email = uniqueEmail();
        register("Alice", email, PASSWORD);
        UUID userId = userRepository.findByEmail(email).orElseThrow().getId();
        JwtService attacker = new JwtService(
                new JwtProperties("attacker-controlled-secret-with-plenty-of-length", Duration.ofHours(1)),
                Clock.systemUTC());

        String forged = attacker.generateToken(userId, email);

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthEndpointStaysPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
