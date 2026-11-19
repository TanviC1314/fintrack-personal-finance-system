package com.fintrack.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.account.dto.CreateAccountRequest;
import com.fintrack.account.entity.Account;
import com.fintrack.account.entity.AccountStatus;
import com.fintrack.account.entity.AccountType;
import com.fintrack.account.exception.AccountArchivedException;
import com.fintrack.account.exception.AccountNameAlreadyExistsException;
import com.fintrack.account.exception.AccountNotEmptyException;
import com.fintrack.account.repository.AccountRepository;
import com.fintrack.audit.service.AuditService;
import com.fintrack.common.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private AccountService accountService;

    private Account existingAccount(String name, String balance) {
        return new Account(userId, name, AccountType.CURRENT, "EUR", new BigDecimal(balance));
    }

    // ---- create ----

    @Test
    void createStartsActiveWithCurrentBalanceEqualToOpeningBalanceAndOwnedByCaller() {
        when(accountRepository.existsByUserIdAndNameIgnoreCaseAndStatus(userId, "Everyday", AccountStatus.ACTIVE))
                .thenReturn(false);
        when(accountRepository.saveAndFlush(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        accountService.create(userId,
                new CreateAccountRequest("  Everyday ", AccountType.CURRENT, "EUR", new BigDecimal("120.50")));

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).saveAndFlush(saved.capture());
        Account account = saved.getValue();
        assertThat(account.getUserId()).isEqualTo(userId);
        assertThat(account.getName()).isEqualTo("Everyday");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getOpeningBalance()).isEqualByComparingTo("120.50");
        assertThat(account.getCurrentBalance()).isEqualByComparingTo("120.50");
    }

    @Test
    void createWithoutOpeningBalanceStartsAtZero() {
        when(accountRepository.saveAndFlush(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        Account account = accountService.create(userId,
                new CreateAccountRequest("Cash", AccountType.CASH, "EUR", null));

        assertThat(account.getCurrentBalance()).isEqualByComparingTo("0");
    }

    @Test
    void createRejectsNameAlreadyUsedByAnActiveAccount() {
        when(accountRepository.existsByUserIdAndNameIgnoreCaseAndStatus(userId, "Savings", AccountStatus.ACTIVE))
                .thenReturn(true);

        assertThatThrownBy(() -> accountService.create(userId,
                new CreateAccountRequest("Savings", AccountType.SAVINGS, "EUR", null)))
                .isInstanceOf(AccountNameAlreadyExistsException.class);
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void createTranslatesUniqueIndexRaceIntoNameConflict() {
        when(accountRepository.saveAndFlush(any(Account.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> accountService.create(userId,
                new CreateAccountRequest("Savings", AccountType.SAVINGS, "EUR", null)))
                .isInstanceOf(AccountNameAlreadyExistsException.class);
    }

    // ---- read ----

    @Test
    void getReturnsNotFoundForAnAccountTheCallerDoesNotOwn() {
        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.get(userId, accountId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listHidesArchivedAccountsUnlessAsked() {
        Account active = existingAccount("Active", "0");
        Account archived = existingAccount("Old", "0");
        when(accountRepository.findAllByUserIdAndStatusOrderByCreatedAtAsc(userId, AccountStatus.ACTIVE))
                .thenReturn(List.of(active));
        when(accountRepository.findAllByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(active, archived));

        assertThat(accountService.list(userId, false)).containsExactly(active);
        assertThat(accountService.list(userId, true)).containsExactly(active, archived);
    }

    // ---- rename ----

    @Test
    void renameChangesTheName() {
        Account account = existingAccount("Old name", "10.00");
        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(account));
        when(accountRepository.existsByUserIdAndNameIgnoreCaseAndStatus(userId, "New name", AccountStatus.ACTIVE))
                .thenReturn(false);
        when(accountRepository.saveAndFlush(account)).thenReturn(account);

        Account renamed = accountService.rename(userId, accountId, " New name ");

        assertThat(renamed.getName()).isEqualTo("New name");
        assertThat(renamed.getCurrentBalance()).isEqualByComparingTo("10.00");
    }

    @Test
    void renameRejectsNameUsedByAnotherActiveAccount() {
        Account account = existingAccount("Old name", "0");
        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(account));
        when(accountRepository.existsByUserIdAndNameIgnoreCaseAndStatus(userId, "Taken", AccountStatus.ACTIVE))
                .thenReturn(true);

        assertThatThrownBy(() -> accountService.rename(userId, accountId, "Taken"))
                .isInstanceOf(AccountNameAlreadyExistsException.class);
        assertThat(account.getName()).isEqualTo("Old name");
    }

    @Test
    void renameAllowsChangingOnlyTheCaseOfItsOwnName() {
        Account account = existingAccount("savings", "0");
        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(account));
        when(accountRepository.saveAndFlush(account)).thenReturn(account);

        Account renamed = accountService.rename(userId, accountId, "Savings");

        assertThat(renamed.getName()).isEqualTo("Savings");
        verify(accountRepository, never()).existsByUserIdAndNameIgnoreCaseAndStatus(any(), any(), any());
    }

    @Test
    void renameOfAnArchivedAccountIsRejected() {
        Account account = existingAccount("Old", "0");
        account.archive();
        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.rename(userId, accountId, "Renamed"))
                .isInstanceOf(AccountArchivedException.class);
    }

    // ---- archive ----

    @Test
    void archiveSucceedsWhenBalanceIsZero() {
        Account account = existingAccount("Empty", "0.00");
        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(account));
        when(accountRepository.saveAndFlush(account)).thenReturn(account);

        Account archived = accountService.archive(userId, accountId);

        assertThat(archived.getStatus()).isEqualTo(AccountStatus.ARCHIVED);
    }

    @Test
    void archiveIsRefusedWhileMoneyRemainsAndLeavesTheAccountActive() {
        Account account = existingAccount("Funded", "0.01");
        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.archive(userId, accountId))
                .isInstanceOf(AccountNotEmptyException.class);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void archivingAnAlreadyArchivedAccountIsANoOp() {
        Account account = existingAccount("Closed", "0");
        account.archive();
        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(account));

        Account result = accountService.archive(userId, accountId);

        assertThat(result.getStatus()).isEqualTo(AccountStatus.ARCHIVED);
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void archiveOfSomeoneElsesAccountLooksLikeNotFound() {
        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.archive(userId, accountId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
