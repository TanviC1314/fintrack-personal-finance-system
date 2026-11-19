CREATE TABLE transactions (
    id               UUID           PRIMARY KEY,
    account_id       UUID           NOT NULL REFERENCES accounts (id),
    -- Amounts are always positive; the type decides whether money comes in or goes out.
    type             VARCHAR(20)    NOT NULL,
    amount           NUMERIC(19, 2) NOT NULL,
    -- Free text for now; becomes a reference to the categories table when categories are added.
    category         VARCHAR(50),
    description      VARCHAR(255),
    transaction_date DATE           NOT NULL,
    -- Set when the transaction is reversed ("deleted"). The row is kept so history stays auditable.
    reversed_at      TIMESTAMPTZ,
    version          BIGINT         NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_transactions_type CHECK (type IN ('INCOME', 'EXPENSE', 'TRANSFER_IN', 'TRANSFER_OUT')),
    CONSTRAINT ck_transactions_amount_positive CHECK (amount > 0)
);

-- History and monthly aggregates are always per account and date range.
CREATE INDEX idx_transactions_account_date ON transactions (account_id, transaction_date DESC);
