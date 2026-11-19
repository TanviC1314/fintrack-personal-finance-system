CREATE TABLE transfers (
    id                     UUID           PRIMARY KEY,
    user_id                UUID           NOT NULL REFERENCES users (id),
    source_account_id      UUID           NOT NULL REFERENCES accounts (id),
    destination_account_id UUID           NOT NULL REFERENCES accounts (id),
    amount                 NUMERIC(19, 2) NOT NULL,
    description            VARCHAR(255),
    transfer_date          DATE           NOT NULL,
    -- Client-chosen key that makes a retried request return this transfer instead of creating another.
    idempotency_key        VARCHAR(100)   NOT NULL,
    created_at             TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_transfers_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_transfers_different_accounts CHECK (source_account_id <> destination_account_id),
    -- Also the last line of defence when two identical requests arrive at the same moment.
    CONSTRAINT uq_transfers_user_idempotency_key UNIQUE (user_id, idempotency_key)
);

CREATE INDEX idx_transfers_user_created ON transfers (user_id, created_at DESC);

-- Each transfer produces two ledger rows. Link them back to the transfer.
ALTER TABLE transactions ADD COLUMN transfer_id UUID REFERENCES transfers (id);

-- Only transfer legs carry a transfer id, and every leg must carry one.
ALTER TABLE transactions ADD CONSTRAINT ck_transactions_transfer_link
    CHECK ((type IN ('TRANSFER_IN', 'TRANSFER_OUT')) = (transfer_id IS NOT NULL));

-- A transfer has exactly one debit leg and one credit leg.
CREATE UNIQUE INDEX uq_transactions_transfer_leg ON transactions (transfer_id, type) WHERE transfer_id IS NOT NULL;
