package com.fintrack.transaction.entity;

public enum TransactionType {
    INCOME(true),
    EXPENSE(false),
    // Created only by the transfer service; they cannot be created or edited directly.
    TRANSFER_IN(true),
    TRANSFER_OUT(false);

    private final boolean credit;

    TransactionType(boolean credit) {
        this.credit = credit;
    }

    /** True when the transaction adds money to its account. */
    public boolean isCredit() {
        return credit;
    }

    public boolean isTransfer() {
        return this == TRANSFER_IN || this == TRANSFER_OUT;
    }
}
