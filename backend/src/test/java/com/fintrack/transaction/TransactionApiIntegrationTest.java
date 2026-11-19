package com.fintrack.transaction;

import static org.assertj.core.api.Assertions.assertThat;
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
class TransactionApiIntegrationTest {

    private static final String DAY = "2025-03-10";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private TestApi api;
    private Session alice;
    private Session bob;
    private UUID aliceAccount;

    @BeforeEach
    void setUp() throws Exception {
        api = new TestApi(mockMvc, objectMapper);
        TestUsers users = new TestUsers(mockMvc, objectMapper);
        alice = users.registerAndLogin("Alice");
        bob = users.registerAndLogin("Bob");
        aliceAccount = api.createAccount(alice, "Everyday", "100.00");
    }

    private ResultActions getTransaction(Session as, UUID id) throws Exception {
        return mockMvc.perform(get("/api/v1/transactions/" + id).header(HttpHeaders.AUTHORIZATION, as.authorization()));
    }

    private ResultActions putTransaction(Session as, UUID id, String json) throws Exception {
        return mockMvc.perform(put("/api/v1/transactions/" + id)
                .header(HttpHeaders.AUTHORIZATION, as.authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions deleteTransaction(Session as, UUID id) throws Exception {
        return mockMvc.perform(delete("/api/v1/transactions/" + id).header(HttpHeaders.AUTHORIZATION, as.authorization()));
    }

    private String update(String amount) throws Exception {
        return update(amount, api.categoryId(alice, "Updated"));
    }

    private static String update(String amount, UUID categoryId) {
        String category = categoryId == null ? "null" : "\"" + categoryId + "\"";
        return "{\"amount\":" + amount + ",\"categoryId\":" + category
                + ",\"description\":\"edited\",\"transactionDate\":\"" + DAY + "\"}";
    }

    // ---- create ----

    @Test
    void incomeIsRecordedAndIncreasesTheAccountBalance() throws Exception {
        api.postTransaction(alice, aliceAccount, "INCOME", "50.25", "Salary", DAY)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("/api/v1/transactions/")))
                .andExpect(jsonPath("$.type").value("INCOME"))
                .andExpect(jsonPath("$.amount").value(50.25))
                .andExpect(jsonPath("$.categoryName").value("Salary"))
                .andExpect(jsonPath("$.categoryId").exists())
                .andExpect(jsonPath("$.accountId").value(aliceAccount.toString()))
                .andExpect(jsonPath("$.reversedAt").doesNotExist());

        assertThat(api.balanceOf(alice, aliceAccount)).isEqualByComparingTo("150.25");
    }

    @Test
    void expenseIsRecordedAndDecreasesTheAccountBalance() throws Exception {
        api.postTransaction(alice, aliceAccount, "EXPENSE", "30.00", "Food", DAY).andExpect(status().isCreated());

        assertThat(api.balanceOf(alice, aliceAccount)).isEqualByComparingTo("70.00");
    }

    @Test
    void createdTransactionCanBeReadBack() throws Exception {
        UUID id = api.createTransaction(alice, aliceAccount, "EXPENSE", "12.00", "Food", DAY);

        getTransaction(alice, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.transactionDate").value(DAY));
    }

    // ---- insufficient funds ----

    @Test
    void expenseBeyondTheBalanceIsRejectedAndChangesNothing() throws Exception {
        api.postTransaction(alice, aliceAccount, "EXPENSE", "100.01", "Rent", DAY)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Business rule violated"));

        assertThat(api.balanceOf(alice, aliceAccount)).isEqualByComparingTo("100.00");
        mockMvc.perform(get("/api/v1/transactions").header(HttpHeaders.AUTHORIZATION, alice.authorization()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ---- validation ----

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-5", "0.001", "12.345", "abc"})
    void invalidAmountsAreRejected(String amount) throws Exception {
        api.postTransactionJson(alice, """
                {"accountId":"%s","type":"EXPENSE","amount":%s,"transactionDate":"%s"}
                """.formatted(aliceAccount, amount.equals("abc") ? "\"abc\"" : amount, DAY))
                .andExpect(status().isBadRequest());

        assertThat(api.balanceOf(alice, aliceAccount)).isEqualByComparingTo("100.00");
    }

    @Test
    void missingRequiredFieldsAreAllReported() throws Exception {
        api.postTransactionJson(alice, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='accountId')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='type')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='amount')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='transactionDate')]").exists());
    }

    @Test
    void transferTypesCannotBeCreatedDirectly() throws Exception {
        api.postTransaction(alice, aliceAccount, "TRANSFER_IN", "10.00", null, DAY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='manualType')]").exists());

        assertThat(api.balanceOf(alice, aliceAccount)).isEqualByComparingTo("100.00");
    }

    @Test
    void futureDatesAreRejected() throws Exception {
        String farFuture = LocalDate.now().plusDays(30).toString();

        api.postTransaction(alice, aliceAccount, "INCOME", "10.00", null, farFuture)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Transaction date cannot be in the future"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2025-13-45", "10/03/2025", "not-a-date"})
    void malformedDatesAreRejected(String date) throws Exception {
        api.postTransaction(alice, aliceAccount, "INCOME", "10.00", null, date).andExpect(status().isBadRequest());
    }

    @Test
    void overlongDescriptionIsRejected() throws Exception {
        api.postTransactionJson(alice, """
                {"accountId":"%s","type":"INCOME","amount":1.00,"description":"%s","transactionDate":"%s"}
                """.formatted(aliceAccount, "d".repeat(256), DAY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='description')]").exists());
    }

    // ---- categories ----

    @Test
    void unknownCategoryIsNotFoundAndChangesNothing() throws Exception {
        api.postTransactionJson(alice, """
                {"accountId":"%s","type":"EXPENSE","amount":10.00,"categoryId":"%s","transactionDate":"%s"}
                """.formatted(aliceAccount, UUID.randomUUID(), DAY))
                .andExpect(status().isNotFound());

        assertThat(api.balanceOf(alice, aliceAccount)).isEqualByComparingTo("100.00");
    }

    @Test
    void anotherUsersCategoryCannotBeUsed() throws Exception {
        UUID bobsCategory = api.categoryId(bob, "Bobs private category");

        api.postTransactionJson(alice, """
                {"accountId":"%s","type":"EXPENSE","amount":10.00,"categoryId":"%s","transactionDate":"%s"}
                """.formatted(aliceAccount, bobsCategory, DAY))
                .andExpect(status().isNotFound());
        UUID id = api.createTransaction(alice, aliceAccount, "EXPENSE", "10.00", null, DAY);
        putTransaction(alice, id, update("10.00", bobsCategory)).andExpect(status().isNotFound());

        assertThat(api.balanceOf(alice, aliceAccount)).isEqualByComparingTo("90.00");
    }

    @Test
    void aTransactionWithoutACategoryHasNullCategoryFields() throws Exception {
        UUID id = api.createTransaction(alice, aliceAccount, "EXPENSE", "10.00", null, DAY);

        getTransaction(alice, id)
                .andExpect(jsonPath("$.categoryId").doesNotExist())
                .andExpect(jsonPath("$.categoryName").doesNotExist());
    }

    @Test
    void updatingCanChangeAndRemoveTheCategory() throws Exception {
        UUID id = api.createTransaction(alice, aliceAccount, "EXPENSE", "10.00", "Food", DAY);

        putTransaction(alice, id, update("10.00", api.categoryId(alice, "Transport")))
                .andExpect(jsonPath("$.categoryName").value("Transport"));
        putTransaction(alice, id, update("10.00", null))
                .andExpect(jsonPath("$.categoryId").doesNotExist());
    }

    @Test
    void aCategoryInUseCannotBeDeletedButAnUnusedOneCan() throws Exception {
        UUID used = api.categoryId(alice, "Used");
        UUID unused = api.categoryId(alice, "Unused");
        api.createTransaction(alice, aliceAccount, "EXPENSE", "10.00", "Used", DAY);

        mockMvc.perform(delete("/api/v1/categories/" + used).header(HttpHeaders.AUTHORIZATION, alice.authorization()))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/v1/categories/" + unused).header(HttpHeaders.AUTHORIZATION, alice.authorization()))
                .andExpect(status().isNoContent());
    }

    @Test
    void renamingACategoryIsReflectedOnItsTransactions() throws Exception {
        UUID id = api.createTransaction(alice, aliceAccount, "EXPENSE", "10.00", "Food", DAY);
        UUID category = api.categoryId(alice, "Food");

        mockMvc.perform(put("/api/v1/categories/" + category)
                .header(HttpHeaders.AUTHORIZATION, alice.authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Eating out\"}")).andExpect(status().isOk());

        getTransaction(alice, id).andExpect(jsonPath("$.categoryName").value("Eating out"));
    }

    // ---- accounts ----

    @Test
    void unknownAccountIsNotFound() throws Exception {
        api.postTransaction(alice, UUID.randomUUID(), "INCOME", "10.00", null, DAY).andExpect(status().isNotFound());
    }

    @Test
    void anotherUsersAccountIsNotFoundAndItsBalanceIsUntouched() throws Exception {
        api.postTransaction(bob, aliceAccount, "INCOME", "999.00", null, DAY).andExpect(status().isNotFound());
        api.postTransaction(bob, aliceAccount, "EXPENSE", "50.00", null, DAY).andExpect(status().isNotFound());

        assertThat(api.balanceOf(alice, aliceAccount)).isEqualByComparingTo("100.00");
    }

    @Test
    void archivedAccountsRejectNewTransactions() throws Exception {
        UUID empty = api.createAccount(alice, "Closing", "0");
        mockMvc.perform(delete("/api/v1/accounts/" + empty).header(HttpHeaders.AUTHORIZATION, alice.authorization()))
                .andExpect(status().isNoContent());

        api.postTransaction(alice, empty, "INCOME", "10.00", null, DAY).andExpect(status().isConflict());
    }

    // ---- update ----

    @Test
    void raisingAnExpenseDebitsTheDifference() throws Exception {
        UUID id = api.createTransaction(alice, aliceAccount, "EXPENSE", "20.00", "Food", DAY);

        putTransaction(alice, id, update("50.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(50.00))
                .andExpect(jsonPath("$.categoryName").value("Updated"));

        assertThat(api.balanceOf(alice, aliceAccount)).isEqualByComparingTo("50.00");
    }

    @Test
    void loweringAnIncomeTakesTheDifferenceBack() throws Exception {
        UUID id = api.createTransaction(alice, aliceAccount, "INCOME", "40.00", "Gift", DAY);

        putTransaction(alice, id, update("10.00")).andExpect(status().isOk());

        assertThat(api.balanceOf(alice, aliceAccount)).isEqualByComparingTo("110.00");
    }

    @Test
    void updateThatWouldOverdrawIsRejectedAndChangesNothing() throws Exception {
        UUID id = api.createTransaction(alice, aliceAccount, "EXPENSE", "20.00", "Food", DAY);

        putTransaction(alice, id, update("500.00")).andExpect(status().isUnprocessableEntity());

        assertThat(api.balanceOf(alice, aliceAccount)).isEqualByComparingTo("80.00");
        getTransaction(alice, id).andExpect(jsonPath("$.amount").value(20.00));
    }

    // ---- reverse ----

    @Test
    void reversingAnExpenseRestoresTheBalanceAndKeepsTheRecord() throws Exception {
        UUID id = api.createTransaction(alice, aliceAccount, "EXPENSE", "30.00", "Food", DAY);

        deleteTransaction(alice, id).andExpect(status().isNoContent());

        assertThat(api.balanceOf(alice, aliceAccount)).isEqualByComparingTo("100.00");
        getTransaction(alice, id).andExpect(status().isOk()).andExpect(jsonPath("$.reversedAt").exists());
    }

    @Test
    void reversingTwiceDoesNotCreditTwice() throws Exception {
        UUID id = api.createTransaction(alice, aliceAccount, "EXPENSE", "30.00", "Food", DAY);

        deleteTransaction(alice, id).andExpect(status().isNoContent());
        deleteTransaction(alice, id).andExpect(status().isNoContent());

        assertThat(api.balanceOf(alice, aliceAccount)).isEqualByComparingTo("100.00");
    }

    @Test
    void reversedTransactionsCannotBeEdited() throws Exception {
        UUID id = api.createTransaction(alice, aliceAccount, "EXPENSE", "30.00", "Food", DAY);
        deleteTransaction(alice, id).andExpect(status().isNoContent());

        putTransaction(alice, id, update("10.00")).andExpect(status().isConflict());
    }

    @Test
    void incomeThatHasAlreadyBeenSpentCannotBeReversed() throws Exception {
        UUID income = api.createTransaction(alice, aliceAccount, "INCOME", "50.00", "Gift", DAY);
        api.createTransaction(alice, aliceAccount, "EXPENSE", "140.00", "Rent", DAY);

        deleteTransaction(alice, income).andExpect(status().isUnprocessableEntity());

        assertThat(api.balanceOf(alice, aliceAccount)).isEqualByComparingTo("10.00");
        getTransaction(alice, income).andExpect(jsonPath("$.reversedAt").doesNotExist());
    }

    // ---- data isolation ----

    @Test
    void anotherUsersTransactionIsNotFoundForReadUpdateAndReverse() throws Exception {
        UUID id = api.createTransaction(alice, aliceAccount, "EXPENSE", "30.00", "Food", DAY);

        getTransaction(bob, id).andExpect(status().isNotFound());
        putTransaction(bob, id, update("1.00")).andExpect(status().isNotFound());
        deleteTransaction(bob, id).andExpect(status().isNotFound());

        getTransaction(alice, id)
                .andExpect(jsonPath("$.amount").value(30.00))
                .andExpect(jsonPath("$.reversedAt").doesNotExist());
        assertThat(api.balanceOf(alice, aliceAccount)).isEqualByComparingTo("70.00");
    }

    @Test
    void unknownTransactionIdIsNotFound() throws Exception {
        getTransaction(alice, UUID.randomUUID()).andExpect(status().isNotFound());
    }

    // ---- authentication ----

    @Test
    void everyTransactionEndpointRequiresAuthentication() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/transactions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/transactions/" + id)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/transactions/" + id).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/transactions/" + id)).andExpect(status().isUnauthorized());
    }
}
