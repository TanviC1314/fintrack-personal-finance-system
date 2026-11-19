CREATE TABLE budgets (
    id          UUID           PRIMARY KEY,
    user_id     UUID           NOT NULL REFERENCES users (id),
    category_id UUID           NOT NULL REFERENCES categories (id),
    -- Spending is only counted from accounts in this currency; amounts in different currencies cannot be added.
    currency    VARCHAR(3)     NOT NULL,
    -- First day of the month the budget applies to.
    month       DATE           NOT NULL,
    amount      NUMERIC(19, 2) NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_budgets_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_budgets_month_is_first_day CHECK (month = date_trunc('month', month)::date),
    CONSTRAINT ck_budgets_currency CHECK (currency ~ '^[A-Z]{3}$'),
    -- One budget per category, month and currency.
    CONSTRAINT uq_budgets_category_month_currency UNIQUE (category_id, month, currency)
);

CREATE INDEX idx_budgets_user_month ON budgets (user_id, month);
