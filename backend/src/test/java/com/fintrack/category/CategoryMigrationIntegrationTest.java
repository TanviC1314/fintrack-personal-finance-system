package com.fintrack.category;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Runs the data migration that replaces free-text transaction labels with category rows, against data
 * created under the old schema. A fresh database would prove nothing about existing rows.
 */
class CategoryMigrationIntegrationTest {

    private static Flyway flywayUpTo(PostgreSQLContainer<?> db, String target) {
        return Flyway.configure()
                .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }

    private static UUID insertUser(Statement sql, String email) throws SQLException {
        UUID id = UUID.randomUUID();
        sql.execute("INSERT INTO users (id, name, email, password_hash) VALUES ('%s', 'U', '%s', 'x')".formatted(id, email));
        return id;
    }

    private static UUID insertAccount(Statement sql, UUID userId) throws SQLException {
        UUID id = UUID.randomUUID();
        sql.execute(("INSERT INTO accounts (id, user_id, name, type, currency, opening_balance, current_balance) "
                + "VALUES ('%s', '%s', 'Main', 'CURRENT', 'EUR', 0, 0)").formatted(id, userId));
        return id;
    }

    private static UUID insertTransaction(Statement sql, UUID accountId, String label) throws SQLException {
        UUID id = UUID.randomUUID();
        String category = label == null ? "NULL" : "'" + label + "'";
        sql.execute(("INSERT INTO transactions (id, account_id, type, amount, category, transaction_date) "
                + "VALUES ('%s', '%s', 'INCOME', 1.00, %s, '2025-01-01')").formatted(id, accountId, category));
        return id;
    }

    private static List<String> column(Connection connection, String query) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Statement sql = connection.createStatement(); ResultSet rs = sql.executeQuery(query)) {
            while (rs.next()) {
                values.add(rs.getString(1));
            }
        }
        return values;
    }

    @Test
    void existingLabelsBecomePerUserCategoriesAndTransactionsAreRelinked() throws Exception {
        try (PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:16-alpine")) {
            db.start();
            flywayUpTo(db, "7").migrate(); // the schema as it was before transactions referenced categories

            UUID aliceTx1;
            UUID aliceTx2;
            UUID aliceTx3;
            UUID aliceUnlabelled;
            UUID bobTx;
            UUID alice;
            UUID bob;
            try (Connection connection = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                 Statement sql = connection.createStatement()) {
                alice = insertUser(sql, "alice@example.com");
                bob = insertUser(sql, "bob@example.com");
                UUID alicesAccount = insertAccount(sql, alice);
                UUID bobsAccount = insertAccount(sql, bob);
                aliceTx1 = insertTransaction(sql, alicesAccount, "Food");
                aliceTx2 = insertTransaction(sql, alicesAccount, "food"); // same category, different case
                aliceTx3 = insertTransaction(sql, alicesAccount, "Transport");
                aliceUnlabelled = insertTransaction(sql, alicesAccount, null);
                bobTx = insertTransaction(sql, bobsAccount, "Food"); // same label, different user
            }

            flywayUpTo(db, "latest").migrate();

            try (Connection connection = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword())) {
                assertThat(column(connection, "SELECT lower(name) FROM categories WHERE user_id = '" + alice + "' ORDER BY 1"))
                        .containsExactly("food", "transport");
                assertThat(column(connection, "SELECT lower(name) FROM categories WHERE user_id = '" + bob + "'"))
                        .containsExactly("food");

                String categoryOf = "SELECT c.name FROM transactions t JOIN categories c ON c.id = t.category_id WHERE t.id = '%s'";
                assertThat(column(connection, categoryOf.formatted(aliceTx1))).hasSize(1);
                assertThat(column(connection, "SELECT category_id FROM transactions WHERE id = '" + aliceTx1 + "'"))
                        .isEqualTo(column(connection, "SELECT category_id FROM transactions WHERE id = '" + aliceTx2 + "'"));
                assertThat(column(connection, categoryOf.formatted(aliceTx3))).containsExactly("Transport");
                assertThat(column(connection, "SELECT category_id FROM transactions WHERE id = '" + aliceUnlabelled + "'"))
                        .containsExactly((String) null);

                // Bob's transaction points at Bob's own category, never at Alice's of the same name.
                assertThat(column(connection, "SELECT c.user_id::text FROM transactions t "
                        + "JOIN categories c ON c.id = t.category_id WHERE t.id = '" + bobTx + "'"))
                        .containsExactly(bob.toString());

                assertThat(column(connection, "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_name = 'transactions' AND column_name = 'category'")).isEmpty();
            }
        }
    }
}
