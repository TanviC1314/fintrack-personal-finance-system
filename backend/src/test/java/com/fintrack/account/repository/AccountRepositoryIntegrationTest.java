package com.fintrack.account.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fintrack.account.entity.Account;
import com.fintrack.account.entity.AccountStatus;
import com.fintrack.account.entity.AccountType;
import com.fintrack.support.PostgresTestContainerConfig;
import com.fintrack.user.entity.User;
import com.fintrack.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestContainerConfig.class)
class AccountRepositoryIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private UserRepository userRepository;

    private UUID aliceId;
    private UUID bobId;

    @BeforeEach
    void createUsers() {
        aliceId = userRepository.saveAndFlush(new User("Alice", uniqueEmail(), "hash")).getId();
        bobId = userRepository.saveAndFlush(new User("Bob", uniqueEmail(), "hash")).getId();
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private Account saveAccount(UUID userId, String name, String openingBalance) {
        return accountRepository.saveAndFlush(
                new Account(userId, name, AccountType.CURRENT, "EUR", new BigDecimal(openingBalance)));
    }

    @Test
    void newAccountStartsActiveWithCurrentBalanceEqualToOpeningBalance() {
        Account saved = saveAccount(aliceId, "Everyday", "250.50");

        Account found = accountRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(found.getOpeningBalance()).isEqualByComparingTo("250.50");
        assertThat(found.getCurrentBalance()).isEqualByComparingTo("250.50");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void accountIsOnlyVisibleToItsOwner() {
        Account alicesAccount = saveAccount(aliceId, "Everyday", "100.00");

        assertThat(accountRepository.findByIdAndUserId(alicesAccount.getId(), aliceId)).isPresent();
        assertThat(accountRepository.findByIdAndUserId(alicesAccount.getId(), bobId)).isEmpty();
    }

    @Test
    void listingReturnsOnlyTheCallersAccountsInCreationOrder() {
        Account first = saveAccount(aliceId, "First", "0.00");
        Account second = saveAccount(aliceId, "Second", "0.00");
        saveAccount(bobId, "Bobs", "0.00");

        List<Account> alices = accountRepository.findAllByUserIdOrderByCreatedAtAsc(aliceId);

        assertThat(alices).extracting(Account::getId).containsExactly(first.getId(), second.getId());
    }

    @Test
    void listingCanBeFilteredByStatus() {
        Account kept = saveAccount(aliceId, "Kept", "0.00");
        Account closed = saveAccount(aliceId, "Closed", "0.00");
        closed.archive();
        accountRepository.saveAndFlush(closed);

        assertThat(accountRepository.findAllByUserIdAndStatusOrderByCreatedAtAsc(aliceId, AccountStatus.ACTIVE))
                .extracting(Account::getId).containsExactly(kept.getId());
    }

    @Test
    void activeAccountNameMustBeUniquePerUserIgnoringCase() {
        saveAccount(aliceId, "Savings", "0.00");

        assertThatThrownBy(() -> saveAccount(aliceId, "SAVINGS", "0.00"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void differentUsersMayUseTheSameAccountName() {
        saveAccount(aliceId, "Savings", "0.00");

        Account bobs = saveAccount(bobId, "Savings", "0.00");

        assertThat(bobs.getId()).isNotNull();
    }

    @Test
    void nameCanBeReusedOnceTheOldAccountIsArchived() {
        Account old = saveAccount(aliceId, "Savings", "0.00");
        old.archive();
        accountRepository.saveAndFlush(old);

        Account replacement = saveAccount(aliceId, "Savings", "0.00");

        assertThat(replacement.getId()).isNotEqualTo(old.getId());
        assertThat(accountRepository.existsByUserIdAndNameIgnoreCaseAndStatus(
                aliceId, "savings", AccountStatus.ACTIVE)).isTrue();
    }

    @Test
    void databaseRejectsNegativeBalance() {
        assertThatThrownBy(() -> saveAccount(aliceId, "Overdrawn", "-0.01"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsAccountForUnknownUser() {
        assertThatThrownBy(() -> saveAccount(UUID.randomUUID(), "Orphan", "0.00"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsMalformedCurrencyCode() {
        assertThatThrownBy(() -> accountRepository.saveAndFlush(
                new Account(aliceId, "Bad currency", AccountType.CASH, "eur", BigDecimal.ZERO)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
