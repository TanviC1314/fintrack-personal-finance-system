package com.fintrack.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.account.entity.Account;
import com.fintrack.account.entity.AccountType;
import com.fintrack.account.exception.AccountArchivedException;
import com.fintrack.account.exception.InsufficientFundsException;
import com.fintrack.account.service.AccountService;
import com.fintrack.audit.service.AuditService;
import com.fintrack.common.exception.InvalidRequestException;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.transaction.dto.CreateTransactionRequest;
import com.fintrack.transaction.dto.UpdateTransactionRequest;
import com.fintrack.transaction.entity.Transaction;
import com.fintrack.transaction.entity.TransactionType;
import com.fintrack.transaction.exception.TransactionNotEditableException;
import com.fintrack.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-15T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 15);

    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID transactionId = UUID.randomUUID();

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AccountService accountService;
    @Mock
    private AuditService auditService;

    private TransactionService service;
    private Account account;

    @BeforeEach
    void setUp() {
        service = new TransactionService(transactionRepository, accountService, auditService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        account = new Account(userId, "Main", AccountType.CURRENT, "EUR", new BigDecimal("100.00"));
        ReflectionTestUtils.setField(account, "id", accountId);
    }

    // ---- helpers ----

    private void accountIsLockable() {
        when(accountService.getForUpdate(userId, accountId)).thenReturn(account);
    }

    private CreateTransactionRequest create(TransactionType type, String amount, LocalDate date) {
        return new CreateTransactionRequest(accountId, type, new BigDecimal(amount), "Food", "Lunch", date);
    }

    /** An existing transaction whose effect is already reflected in the account balance. */
    private Transaction existing(TransactionType type, String amount) {
        accountIsLockable();
        when(transactionRepository.findAccountIdByIdAndOwner(transactionId, userId)).thenReturn(Optional.of(accountId));
        Transaction transaction = new Transaction(account, type, new BigDecimal(amount), "Food", "Lunch", TODAY);
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        return transaction;
    }

    private UpdateTransactionRequest update(String amount) {
        return new UpdateTransactionRequest(new BigDecimal(amount), "Groceries", "Weekly shop", TODAY);
    }

    // ---- create ----

    @Test
    void incomeIncreasesTheBalanceAndIsRecordedInTheLedger() {
        accountIsLockable();
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(userId, create(TransactionType.INCOME, "50.25", TODAY));

        assertThat(account.getCurrentBalance()).isEqualByComparingTo("150.25");
        ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(TransactionType.INCOME);
        assertThat(saved.getValue().getAmount()).isEqualTo(new BigDecimal("50.25"));
        assertThat(saved.getValue().getAccountId()).isEqualTo(accountId);
    }

    @Test
    void expenseDecreasesTheBalance() {
        accountIsLockable();
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(userId, create(TransactionType.EXPENSE, "30.00", TODAY));

        assertThat(account.getCurrentBalance()).isEqualByComparingTo("70.00");
    }

    @Test
    void amountIsStoredWithTwoDecimalPlacesAndTextIsTidied() {
        accountIsLockable();
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction saved = service.create(userId, new CreateTransactionRequest(
                accountId, TransactionType.INCOME, new BigDecimal("5"), "  Salary ", "   ", TODAY));

        assertThat(saved.getAmount().scale()).isEqualTo(2);
        assertThat(saved.getCategory()).isEqualTo("Salary");
        assertThat(saved.getDescription()).isNull();
    }

    @Test
    void expenseCanSpendTheWholeBalance() {
        accountIsLockable();
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(userId, create(TransactionType.EXPENSE, "100.00", TODAY));

        assertThat(account.getCurrentBalance()).isEqualByComparingTo("0");
    }

    @Test
    void expenseBeyondTheBalanceIsRefusedAndNothingIsSaved() {
        accountIsLockable();

        assertThatThrownBy(() -> service.create(userId, create(TransactionType.EXPENSE, "100.01", TODAY)))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(account.getCurrentBalance()).isEqualByComparingTo("100.00");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transfersCannotBeCreatedAsPlainTransactions() {
        assertThatThrownBy(() -> service.create(userId, create(TransactionType.TRANSFER_OUT, "10.00", TODAY)))
                .isInstanceOf(InvalidRequestException.class);

        verify(accountService, never()).getForUpdate(any(), any());
    }

    @Test
    void futureDatesAreRejectedButTodayAndTomorrowAreAccepted() {
        assertThatThrownBy(() -> service.create(userId, create(TransactionType.INCOME, "1.00", TODAY.plusDays(2))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("future");

        accountIsLockable();
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        service.create(userId, create(TransactionType.INCOME, "1.00", TODAY));
        service.create(userId, create(TransactionType.INCOME, "1.00", TODAY.plusDays(1)));

        assertThat(account.getCurrentBalance()).isEqualByComparingTo("102.00");
    }

    @Test
    void archivedAccountsAcceptNoTransactions() {
        account.archive();
        accountIsLockable();

        assertThatThrownBy(() -> service.create(userId, create(TransactionType.INCOME, "10.00", TODAY)))
                .isInstanceOf(AccountArchivedException.class);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void anotherUsersAccountIsNotFound() {
        when(accountService.getForUpdate(userId, accountId)).thenThrow(new ResourceNotFoundException("Account not found"));

        assertThatThrownBy(() -> service.create(userId, create(TransactionType.INCOME, "10.00", TODAY)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(transactionRepository, never()).save(any());
    }

    // ---- update ----

    @Test
    void raisingAnIncomeAmountCreditsTheDifference() {
        existing(TransactionType.INCOME, "40.00");

        service.update(userId, transactionId, update("65.00"));

        assertThat(account.getCurrentBalance()).isEqualByComparingTo("125.00");
    }

    @Test
    void loweringAnIncomeAmountDebitsTheDifference() {
        existing(TransactionType.INCOME, "40.00");

        service.update(userId, transactionId, update("15.00"));

        assertThat(account.getCurrentBalance()).isEqualByComparingTo("75.00");
    }

    @Test
    void raisingAnExpenseAmountDebitsTheDifference() {
        existing(TransactionType.EXPENSE, "20.00");

        service.update(userId, transactionId, update("50.00"));

        assertThat(account.getCurrentBalance()).isEqualByComparingTo("70.00");
    }

    @Test
    void loweringAnExpenseAmountGivesTheDifferenceBack() {
        existing(TransactionType.EXPENSE, "20.00");

        service.update(userId, transactionId, update("5.00"));

        assertThat(account.getCurrentBalance()).isEqualByComparingTo("115.00");
    }

    @Test
    void raisingAnExpenseBeyondAvailableFundsIsRefusedAndNothingChanges() {
        Transaction transaction = existing(TransactionType.EXPENSE, "20.00");

        assertThatThrownBy(() -> service.update(userId, transactionId, update("120.01")))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(account.getCurrentBalance()).isEqualByComparingTo("100.00");
        assertThat(transaction.getAmount()).isEqualByComparingTo("20.00");
    }

    @Test
    void loweringAnIncomeAfterTheMoneyWasSpentIsRefused() {
        // Lowering a 150 income to 10 must take 140 back out, but the account only holds 100 now.
        existing(TransactionType.INCOME, "150.00");

        assertThatThrownBy(() -> service.update(userId, transactionId, update("10.00")))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(account.getCurrentBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void editingOnlyDetailsLeavesTheBalanceAlone() {
        Transaction transaction = existing(TransactionType.EXPENSE, "20.00");

        service.update(userId, transactionId, update("20.00"));

        assertThat(account.getCurrentBalance()).isEqualByComparingTo("100.00");
        assertThat(transaction.getCategory()).isEqualTo("Groceries");
        assertThat(transaction.getDescription()).isEqualTo("Weekly shop");
    }

    @Test
    void reversedTransactionsCannotBeEdited() {
        Transaction transaction = existing(TransactionType.EXPENSE, "20.00");
        transaction.markReversed(NOW);

        assertThatThrownBy(() -> service.update(userId, transactionId, update("30.00")))
                .isInstanceOf(TransactionNotEditableException.class);
    }

    @Test
    void transferLegsCannotBeEditedIndividually() {
        existing(TransactionType.TRANSFER_OUT, "20.00");

        assertThatThrownBy(() -> service.update(userId, transactionId, update("30.00")))
                .isInstanceOf(TransactionNotEditableException.class);
    }

    @Test
    void transactionsOnAnArchivedAccountCannotBeEdited() {
        existing(TransactionType.EXPENSE, "20.00");
        account.archive();

        assertThatThrownBy(() -> service.update(userId, transactionId, update("30.00")))
                .isInstanceOf(AccountArchivedException.class);
    }

    @Test
    void updatingATransactionOfAnotherUserIsNotFound() {
        when(transactionRepository.findAccountIdByIdAndOwner(transactionId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(userId, transactionId, update("30.00")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(accountService, never()).getForUpdate(any(), any());
    }

    // ---- reverse ----

    @Test
    void reversingAnExpenseGivesTheMoneyBackAndKeepsTheRow() {
        Transaction transaction = existing(TransactionType.EXPENSE, "30.00");

        service.reverse(userId, transactionId);

        assertThat(account.getCurrentBalance()).isEqualByComparingTo("130.00");
        assertThat(transaction.isReversed()).isTrue();
        assertThat(transaction.getReversedAt()).isEqualTo(NOW);
        verify(transactionRepository, never()).delete(any(Transaction.class));
    }

    @Test
    void reversingAnIncomeRemovesTheMoney() {
        existing(TransactionType.INCOME, "30.00");

        service.reverse(userId, transactionId);

        assertThat(account.getCurrentBalance()).isEqualByComparingTo("70.00");
    }

    @Test
    void reversingAnIncomeWhoseMoneyHasBeenSpentIsRefused() {
        Transaction transaction = existing(TransactionType.INCOME, "500.00");

        assertThatThrownBy(() -> service.reverse(userId, transactionId))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(account.getCurrentBalance()).isEqualByComparingTo("100.00");
        assertThat(transaction.isReversed()).isFalse();
    }

    @Test
    void reversingTwiceChangesTheBalanceOnlyOnce() {
        existing(TransactionType.EXPENSE, "30.00");

        service.reverse(userId, transactionId);
        service.reverse(userId, transactionId);

        assertThat(account.getCurrentBalance()).isEqualByComparingTo("130.00");
    }

    @Test
    void transferLegsCannotBeReversedIndividually() {
        existing(TransactionType.TRANSFER_IN, "30.00");

        assertThatThrownBy(() -> service.reverse(userId, transactionId))
                .isInstanceOf(TransactionNotEditableException.class);
    }

    // ---- read ----

    @Test
    void getReturnsNotFoundWhenTheTransactionIsNotTheCallers() {
        when(transactionRepository.findByIdAndAccountUserId(transactionId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(userId, transactionId)).isInstanceOf(ResourceNotFoundException.class);
    }
}
