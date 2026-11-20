package com.fintrack.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fintrack.support.PostgresTestContainerConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Checks the published API description: it is reachable without a token, every operation is documented,
 * bearer authentication is described correctly, and the committed copy in docs/openapi.json is current.
 * To refresh that copy after an API change, run: ./mvnw verify -Dopenapi.update=true
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainerConfig.class)
class OpenApiIntegrationTest {

    private static final Path PUBLISHED_COPY = Path.of("..", "docs", "openapi.json");
    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "delete", "patch");

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);

    private JsonNode fetchSpec() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        ObjectNode spec = (ObjectNode) objectMapper.readTree(body);
        // The server URL depends on where the app runs, so it is not part of the stored description.
        spec.remove("servers");
        return spec;
    }

    private List<JsonNode> operations(JsonNode spec) {
        List<JsonNode> operations = new ArrayList<>();
        spec.get("paths").fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(method -> {
            if (HTTP_METHODS.contains(method.getKey())) {
                ((ObjectNode) method.getValue()).put("_path", path.getKey()).put("_method", method.getKey().toUpperCase());
                operations.add(method.getValue());
            }
        }));
        return operations;
    }

    @Test
    void theApiDescriptionAndSwaggerUiAreReachableWithoutLoggingIn() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void everyEndpointIsDocumentedWithASummaryAndATag() throws Exception {
        List<JsonNode> operations = operations(fetchSpec());

        assertThat(operations).hasSize(26);
        for (JsonNode operation : operations) {
            String name = operation.get("_method").asText() + " " + operation.get("_path").asText();
            assertThat(operation.path("summary").asText()).as("summary of %s", name).isNotBlank();
            assertThat(operation.path("tags")).as("tags of %s", name).isNotEmpty();
        }
    }

    @Test
    void theApiDescriptionCoversTheEndpointsTheProjectPromises() throws Exception {
        JsonNode paths = fetchSpec().get("paths");

        for (String path : List.of(
                "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/accounts", "/api/v1/accounts/{id}",
                "/api/v1/transactions", "/api/v1/transactions/{id}", "/api/v1/transfers",
                "/api/v1/dashboard/summary", "/api/v1/dashboard/monthly", "/api/v1/dashboard/categories",
                "/api/v1/budgets", "/api/v1/budgets/{id}", "/api/v1/categories", "/api/v1/audit-logs")) {
            assertThat(paths.has(path)).as("path %s is documented", path).isTrue();
        }
    }

    @Test
    void bearerAuthenticationIsDescribedAndOnlyTheAuthEndpointsAreOpen() throws Exception {
        JsonNode spec = fetchSpec();

        JsonNode scheme = spec.at("/components/securitySchemes/bearerAuth");
        assertThat(scheme.get("type").asText()).isEqualTo("http");
        assertThat(scheme.get("scheme").asText()).isEqualTo("bearer");
        assertThat(scheme.get("bearerFormat").asText()).isEqualTo("JWT");

        for (JsonNode operation : operations(spec)) {
            String path = operation.get("_path").asText();
            boolean open = path.startsWith("/api/v1/auth/");
            // Operations that need a token inherit the global requirement; open ones declare an empty list.
            boolean declaresEmptySecurity = operation.has("security") && operation.get("security").isEmpty();
            assertThat(declaresEmptySecurity).as("%s %s is open", operation.get("_method").asText(), path).isEqualTo(open);
        }
    }

    @Test
    void theIdempotencyKeyHeaderOfTransfersIsDocumented() throws Exception {
        JsonNode post = fetchSpec().at("/paths/~1api~1v1~1transfers/post");

        JsonNode header = null;
        for (JsonNode parameter : post.get("parameters")) {
            if ("Idempotency-Key".equals(parameter.get("name").asText())) {
                header = parameter;
            }
        }
        assertThat(header).isNotNull();
        assertThat(header.get("in").asText()).isEqualTo("header");
        assertThat(header.get("required").asBoolean()).isTrue();
        assertThat(header.get("description").asText()).contains("same key");
    }

    @Test
    void theAuthenticatedUserIsNotExposedAsARequestParameter() throws Exception {
        for (JsonNode operation : operations(fetchSpec())) {
            for (JsonNode parameter : operation.path("parameters")) {
                assertThat(parameter.get("name").asText()).isNotEqualTo("caller");
            }
        }
    }

    @Test
    void theCommittedCopyInDocsMatchesTheRunningApi() throws Exception {
        JsonNode current = fetchSpec();

        if (Boolean.getBoolean("openapi.update")) {
            Files.createDirectories(PUBLISHED_COPY.getParent());
            Files.writeString(PUBLISHED_COPY,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(sorted(current)) + System.lineSeparator(),
                    StandardCharsets.UTF_8);
            return;
        }

        assertThat(PUBLISHED_COPY).as("docs/openapi.json exists; create it with ./mvnw verify -Dopenapi.update=true").exists();
        JsonNode published = objectMapper.readTree(readString(PUBLISHED_COPY));
        assertThat(published)
                .as("docs/openapi.json is out of date; refresh it with ./mvnw verify -Dopenapi.update=true")
                .isEqualTo(current);
    }

    private static String readString(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** Re-serialises through a tree with sorted keys so the stored file does not change between runs. */
    private JsonNode sorted(JsonNode node) throws IOException {
        return objectMapper.readTree(objectMapper.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writeValueAsString(objectMapper.convertValue(node, Object.class)));
    }
}
