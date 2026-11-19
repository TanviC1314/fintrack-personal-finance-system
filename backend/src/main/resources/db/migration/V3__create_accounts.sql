CREATE TABLE accounts (
    id              UUID           PRIMARY KEY,
    user_id         UUID           NOT NULL REFERENCES users (id),
    name            VARCHAR(100)   NOT NULL,
    type            VARCHAR(20)    NOT NULL,
    currency        VARCHAR(3)     NOT NULL,
    opening_balance NUMERIC(19, 2) NOT NULL,
    -- Cached balance, kept in step with the transaction ledger (see docs/DATABASE_DESIGN.md).
    current_balance NUMERIC(19, 2) NOT NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_accounts_type CHECK (type IN ('CURRENT', 'SAVINGS', 'CASH')),
    CONSTRAINT ck_accounts_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_accounts_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_accounts_opening_balance_non_negative CHECK (opening_balance >= 0),
    -- Last line of defence against overdrafts if application code ever has a bug.
    CONSTRAINT ck_accounts_current_balance_non_negative CHECK (current_balance >= 0)
);

CREATE INDEX idx_accounts_user_id ON accounts (user_id);

-- An active account name is unique per user, ignoring case. Archived accounts do not block reuse.
CREATE UNIQUE INDEX uq_accounts_user_active_name ON accounts (user_id, lower(name)) WHERE status = 'ACTIVE';
