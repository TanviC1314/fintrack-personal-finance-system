package com.fintrack.transfer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.account.entity.Account;
import com.fintrack.account.entity.AccountType;
import com.fintrack.account.exception.AccountArchivedException;
import com.fintrack.account.exception.InsufficientFundsException;
import com.fintrack.account.service.AccountService;
import com.fintrack.audit.service.AuditService;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.InvalidRequestException;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.transaction.entity.Transaction;
import com.fintrack.transaction.entity.TransactionType;
import com.fintrack.transaction.repository.TransactionRepository;
import com.fintrack.transfer.dto.CreateTransferRequest;
import com.fintrack.transfer.entity.Transfer;
import com.fintrack.transfer.exception.IdempotencyKeyReusedException;
import com.fintrack.transfer.repository.TransferRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-15T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 15);
    private static final String KEY = "key-1";

    private final UUID userId = UUID.randomUUID();
    // Fixed ids with a known order: LOW sorts before HIGH.
    private final UUID low = new UUID(1, 0);
    private final UUID high = new UUID(2, 0);

    @Mock
    private TransferRepository transferRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AccountService accountService;
    @Mock
    private AuditService auditService;

    private TransferService service;
    private Account lowAccount;
    private Account highAccount;

    @BeforeEach
    void setUp() {
        service = new TransferService(transferRepository, transactionRepository, accountService, auditService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        lowAccount = account("Low", "EUR", "100.00", low);
        highAccount = account("High", "EUR", "20.00", high);
    }

    private Account account(String name, String currency, String balance, UUID id) {
        Account account = new Account(userId, name, AccountType.CURRENT, currency, new BigDecimal(balance));
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    private void bothAccountsAreLockable() {
        when(accountService.getForUpdate(userId, low)).thenReturn(lowAccount);
        when(accountService.getForUpdate(userId, high)).thenReturn(highAccount);
    }

    private void savesAreEchoed() {
        when(transferRepository.saveAndFlush(any(Transfer.class))).thenAnswer(inv -> {
            Transfer transfer = inv.getArgument(0);
            ReflectionTestUtils.setField(transfer, "id", UUID.randomUUID());
            return transfer;
        });
    }

    private CreateTransferRequest request(UUID from, UUID to, String amount) {
        return new CreateTransferRequest(from, to, new BigDecimal(amount), null, null);
    }

    @Test
    void movesMoneyFromSourceToDestinationAndRecordsBothLedgerLegs() {
        bothAccountsAreLockable();
        savesAreEchoed();

        Transfer transfer = service.transfer(userId, request(low, high, "40.00"), KEY).transfer();

        assertThat(lowAccount.getCurrentBalance()).isEqualByComparingTo("60.00");
        assertThat(highAccount.getCurrentBalance()).isEqualByComparingTo("60.00");
        assertThat(transfer.getAmount()).isEqualByComparingTo("40.00");
        assertThat(transfer.getTransferDate()).isEqualTo(TODAY);
        assertThat(transfer.getIdempotencyKey()).isEqualTo(KEY);

        ArgumentCaptor<Transaction> legs = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, org.mockito.Mockito.times(2)).save(legs.capture());
        Transaction out = legs.getAllValues().get(0);
        Transaction in = legs.getAllValues().get(1);
        assertThat(out.getType()).isEqualTo(TransactionType.TRANSFER_OUT);
        assertThat(out.getAccountId()).isEqualTo(low);
        assertThat(in.getType()).isEqualTo(TransactionType.TRANSFER_IN);
        assertThat(in.getAccountId()).isEqualTo(high);
        assertThat(List.of(out.getTransferId(), in.getTransferId())).containsOnly(transfer.getId());
        assertThat(out.getDescription()).isEqualTo("Transfer to High");
        assertThat(in.getDescription()).isEqualTo("Transfer from Low");
    }

    @Test
    void sourceBalanceCanBeSpentExactly() {
        bothAccountsAreLockable();
        savesAreEchoed();

        service.transfer(userId, request(low, high, "100.00"), KEY);

        assertThat(lowAccount.getCurrentBalance()).isEqualByComparingTo("0");
        assertThat(highAccount.getCurrentBalance()).isEqualByComparingTo("120.00");
    }

    @Test
    void insufficientFundsLeavesBothBalancesAndTheLedgerUntouched() {
        bothAccountsAreLockable();

        assertThatThrownBy(() -> service.transfer(userId, request(low, high, "100.01"), KEY))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(lowAccount.getCurrentBalance()).isEqualByComparingTo("100.00");
        assertThat(highAccount.getCurrentBalance()).isEqualByComparingTo("20.00");
        verify(transferRepository, never()).saveAndFlush(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void accountsAreLockedInIdOrderWhicheverWayTheMoneyFlows() {
        bothAccountsAreLockable();
        savesAreEchoed();

        service.transfer(userId, request(low, high, "10.00"), "k1");
        service.transfer(userId, request(high, low, "5.00"), "k2");

        InOrder order = inOrder(accountService);
        order.verify(accountService).getForUpdate(userId, low);
        order.verify(accountService).getForUpdate(userId, high);
        order.verify(accountService).getForUpdate(userId, low);
        order.verify(accountService).getForUpdate(userId, high);
    }

    @Test
    void sameSourceAndDestinationIsRejectedBeforeAnythingIsLocked() {
        assertThatThrownBy(() -> service.transfer(userId, request(low, low, "10.00"), KEY))
                .isInstanceOf(InvalidRequestException.class);

        verify(accountService, never()).getForUpdate(any(), any());
    }

    @Test
    void anotherUsersAccountIsNotFoundAndNothingChanges() {
        when(accountService.getForUpdate(userId, low)).thenReturn(lowAccount);
        when(accountService.getForUpdate(userId, high)).thenThrow(new ResourceNotFoundException("Account not found"));

        assertThatThrownBy(() -> service.transfer(userId, request(low, high, "10.00"), KEY))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(lowAccount.getCurrentBalance()).isEqualByComparingTo("100.00");
        verify(transferRepository, never()).saveAndFlush(any());
    }

    @Test
    void archivedAccountsCannotTakePartInTransfers() {
        highAccount.archive();
        bothAccountsAreLockable();

        assertThatThrownBy(() -> service.transfer(userId, request(low, high, "10.00"), KEY))
                .isInstanceOf(AccountArchivedException.class);
        assertThat(lowAccount.getCurrentBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void accountsInDifferentCurrenciesAreRefused() {
        highAccount = account("High", "USD", "20.00", high);
        bothAccountsAreLockable();

        assertThatThrownBy(() -> service.transfer(userId, request(low, high, "10.00"), KEY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("currencies");
        assertThat(lowAccount.getCurrentBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void futureDatesAreRejected() {
        CreateTransferRequest future = new CreateTransferRequest(
                low, high, new BigDecimal("10.00"), null, TODAY.plusDays(2));

        assertThatThrownBy(() -> service.transfer(userId, future, KEY)).isInstanceOf(InvalidRequestException.class);
        verify(accountService, never()).getForUpdate(any(), any());
    }

    // ---- idempotency ----

    private Transfer earlierTransfer(UUID from, UUID to, String amount) {
        Transfer earlier = new Transfer(userId, from, to, new BigDecimal(amount), null, TODAY, KEY);
        ReflectionTestUtils.setField(earlier, "id", UUID.randomUUID());
        return earlier;
    }

    @Test
    void repeatingARequestReturnsTheOriginalTransferAndMovesNoMoney() {
        Transfer earlier = earlierTransfer(low, high, "40.00");
        when(transferRepository.findByUserIdAndIdempotencyKey(userId, KEY)).thenReturn(Optional.of(earlier));

        TransferResult result = service.transfer(userId, request(low, high, "40.00"), KEY);

        assertThat(result.replayed()).isTrue();
        assertThat(result.transfer()).isSameAs(earlier);
        assertThat(lowAccount.getCurrentBalance()).isEqualByComparingTo("100.00");
        verify(accountService, never()).getForUpdate(any(), any());
        verify(transferRepository, never()).saveAndFlush(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void aRequestThatLosesTheRaceIsRecognisedAfterTheLocksAreHeld() {
        // Nothing existed at the first check, but the other request committed while we waited for the lock.
        Transfer earlier = earlierTransfer(low, high, "40.00");
        when(transferRepository.findByUserIdAndIdempotencyKey(userId, KEY))
                .thenReturn(Optional.empty(), Optional.of(earlier));
        bothAccountsAreLockable();

        TransferResult result = service.transfer(userId, request(low, high, "40.00"), KEY);

        assertThat(result.replayed()).isTrue();
        assertThat(lowAccount.getCurrentBalance()).isEqualByComparingTo("100.00");
        assertThat(highAccount.getCurrentBalance()).isEqualByComparingTo("20.00");
        verify(transferRepository, never()).saveAndFlush(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void reusingAKeyWithADifferentAmountOrAccountIsRefused() {
        Transfer earlier = earlierTransfer(low, high, "40.00");
        when(transferRepository.findByUserIdAndIdempotencyKey(userId, KEY)).thenReturn(Optional.of(earlier));

        assertThatThrownBy(() -> service.transfer(userId, request(low, high, "41.00"), KEY))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertThatThrownBy(() -> service.transfer(userId, request(high, low, "40.00"), KEY))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertThatThrownBy(() -> service.transfer(userId, request(low, new UUID(3, 0), "40.00"), KEY))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void sameAmountWrittenWithDifferentScaleIsTheSameRequest() {
        Transfer earlier = earlierTransfer(low, high, "40.00");
        when(transferRepository.findByUserIdAndIdempotencyKey(userId, KEY)).thenReturn(Optional.of(earlier));

        assertThat(service.transfer(userId, request(low, high, "40"), KEY).replayed()).isTrue();
    }

    @Test
    void keysAreLookedUpPerUser() {
        bothAccountsAreLockable();
        savesAreEchoed();

        service.transfer(userId, request(low, high, "10.00"), KEY);

        verify(transferRepository, atLeastOnce()).findByUserIdAndIdempotencyKey(userId, KEY);
    }

    @Test
    void aSimultaneousRequestWithTheSameKeyAndOtherAccountsIsReportedAsKeyReuse() {
        bothAccountsAreLockable();
        when(transferRepository.saveAndFlush(any(Transfer.class))).thenThrow(new DataIntegrityViolationException(
                "duplicate", new RuntimeException("violates unique constraint \"uq_transfers_user_idempotency_key\"")));

        assertThatThrownBy(() -> service.transfer(userId, request(low, high, "10.00"), KEY))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void otherConstraintViolationsAreNotMistakenForKeyReuse() {
        bothAccountsAreLockable();
        when(transferRepository.saveAndFlush(any(Transfer.class))).thenThrow(new DataIntegrityViolationException(
                "other", new RuntimeException("violates check constraint \"ck_transfers_amount_positive\"")));

        assertThatThrownBy(() -> service.transfer(userId, request(low, high, "10.00"), KEY))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"has space", "tab\there", "naïve-key"})
    void malformedKeysAreRejected(String key) {
        assertThatThrownBy(() -> service.transfer(userId, request(low, high, "10.00"), key))
                .isInstanceOf(InvalidRequestException.class);
        verify(accountService, never()).getForUpdate(any(), any());
    }

    @Test
    void keysLongerThan100CharactersAreRejected() {
        assertThatThrownBy(() -> service.transfer(userId, request(low, high, "10.00"), "k".repeat(101)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void aKeyOfExactly100CharactersIsAccepted() {
        bothAccountsAreLockable();
        savesAreEchoed();

        assertThat(service.transfer(userId, request(low, high, "10.00"), "k".repeat(100)).replayed()).isFalse();
    }

    @Test
    void userDescriptionIsUsedOnBothLegs() {
        bothAccountsAreLockable();
        savesAreEchoed();
        CreateTransferRequest withNote = new CreateTransferRequest(
                low, high, new BigDecimal("10.00"), "  Monthly saving ", TODAY);

        service.transfer(userId, withNote, KEY);

        ArgumentCaptor<Transaction> legs = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, org.mockito.Mockito.times(2)).save(legs.capture());
        assertThat(legs.getAllValues()).extracting(Transaction::getDescription).containsOnly("Monthly saving");
    }
}
