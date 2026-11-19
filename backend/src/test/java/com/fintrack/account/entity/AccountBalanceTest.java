package com.fintrack.account.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fintrack.account.exception.InsufficientFundsException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AccountBalanceTest {

    private Account accountWith(String balance) {
        return new Account(UUID.randomUUID(), "Main", AccountType.CURRENT, "EUR", new BigDecimal(balance));
    }

    @Test
    void creditAddsToTheBalanceWithoutTouchingTheOpeningBalance() {
        Account account = accountWith("100.00");

        account.credit(new BigDecimal("25.50"));

        assertThat(account.getCurrentBalance()).isEqualByComparingTo("125.50");
        assertThat(account.getOpeningBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void debitSubtractsFromTheBalance() {
        Account account = accountWith("100.00");

        account.debit(new BigDecimal("40.01"));

        assertThat(account.getCurrentBalance()).isEqualByComparingTo("59.99");
    }

    @Test
    void debitOfTheWholeBalanceIsAllowed() {
        Account account = accountWith("100.00");

        account.debit(new BigDecimal("100.00"));

        assertThat(account.getCurrentBalance()).isEqualByComparingTo("0");
    }

    @Test
    void debitBeyondTheBalanceIsRefusedAndLeavesTheBalanceUnchanged() {
        Account account = accountWith("100.00");

        assertThatThrownBy(() -> account.debit(new BigDecimal("100.01")))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(account.getCurrentBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void repeatedSmallAmountsDoNotAccumulateRoundingError() {
        Account account = accountWith("0.00");

        for (int i = 0; i < 10; i++) {
            account.credit(new BigDecimal("0.10"));
        }

        // With double this famously drifts away from exactly 1.00.
        assertThat(account.getCurrentBalance()).isEqualByComparingTo("1.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5.00"})
    void creditAndDebitRejectNonPositiveAmounts(String amount) {
        Account account = accountWith("10.00");

        assertThatThrownBy(() -> account.credit(new BigDecimal(amount))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.debit(new BigDecimal(amount))).isInstanceOf(IllegalArgumentException.class);
    }
}
