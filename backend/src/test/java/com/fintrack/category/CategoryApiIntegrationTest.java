package com.fintrack.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.support.PostgresTestContainerConfig;
import com.fintrack.support.TestUsers;
import com.fintrack.support.TestUsers.Session;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
class CategoryApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private Session alice;
    private Session bob;

    @BeforeEach
    void setUp() throws Exception {
        TestUsers users = new TestUsers(mockMvc, objectMapper);
        alice = users.registerAndLogin("Alice");
        bob = users.registerAndLogin("Bob");
    }

    private ResultActions create(Session as, String name) throws Exception {
        return mockMvc.perform(post("/api/v1/categories")
                .header(HttpHeaders.AUTHORIZATION, as.authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}"));
    }

    private UUID createdId(Session as, String name) throws Exception {
        String body = create(as, name).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private ResultActions rename(Session as, UUID id, String name) throws Exception {
        return mockMvc.perform(put("/api/v1/categories/" + id)
                .header(HttpHeaders.AUTHORIZATION, as.authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}"));
    }

    private ResultActions list(Session as) throws Exception {
        return mockMvc.perform(get("/api/v1/categories").header(HttpHeaders.AUTHORIZATION, as.authorization()));
    }

    @Test
    void createReturns201WithLocation() throws Exception {
        create(alice, "Groceries")
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("/api/v1/categories/")))
                .andExpect(jsonPath("$.name").value("Groceries"))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void namesAreTrimmedAndUniquePerUserIgnoringCase() throws Exception {
        createdId(alice, "  Food ");

        create(alice, "FOOD").andExpect(status().isConflict());
        create(bob, "Food").andExpect(status().isCreated());
        list(alice).andExpect(jsonPath("$[0].name").value("Food"));
    }

    @Test
    void blankAndOverlongNamesAreRejected() throws Exception {
        create(alice, "   ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='name')]").exists());
        create(alice, "n".repeat(51)).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/categories")
                        .header(HttpHeaders.AUTHORIZATION, alice.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listIsSortedByNameAndContainsOnlyTheCallersCategories() throws Exception {
        createdId(alice, "Transport");
        createdId(alice, "Food");
        createdId(bob, "Bobs");

        list(alice)
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Food"))
                .andExpect(jsonPath("$[1].name").value("Transport"));
    }

    @Test
    void renameChangesTheNameAndConflictsAreDetected() throws Exception {
        UUID food = createdId(alice, "Food");
        createdId(alice, "Transport");

        rename(alice, food, "Eating out").andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Eating out"));
        rename(alice, food, "transport").andExpect(status().isConflict());
        rename(alice, food, "EATING OUT").andExpect(status().isOk());
    }

    @Test
    void deleteRemovesTheCategory() throws Exception {
        UUID id = createdId(alice, "Temporary");

        mockMvc.perform(delete("/api/v1/categories/" + id).header(HttpHeaders.AUTHORIZATION, alice.authorization()))
                .andExpect(status().isNoContent());

        list(alice).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void anotherUsersCategoryIsNotFoundForRenameAndDeleteAndStaysUntouched() throws Exception {
        UUID alices = createdId(alice, "Private");

        rename(bob, alices, "Hijacked").andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/categories/" + alices).header(HttpHeaders.AUTHORIZATION, bob.authorization()))
                .andExpect(status().isNotFound());

        list(alice).andExpect(jsonPath("$[0].name").value("Private"));
        assertThat(list(bob).andReturn().getResponse().getContentAsString()).isEqualTo("[]");
    }

    @Test
    void endpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/categories")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/categories").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/categories/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }
}
