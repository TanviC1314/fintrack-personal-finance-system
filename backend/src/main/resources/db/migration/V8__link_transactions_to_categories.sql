-- Replace the free-text transactions.category label with a reference to the categories table.

-- 1. Create one category per user for each distinct existing label. Labels that differ only in
--    letter case become one category (min() picks a single spelling to keep).
INSERT INTO categories (id, user_id, name)
SELECT gen_random_uuid(), a.user_id, min(t.category)
FROM transactions t
         JOIN accounts a ON a.id = t.account_id
WHERE t.category IS NOT NULL
GROUP BY a.user_id, lower(t.category);

-- 2. Point every labelled transaction at its category.
ALTER TABLE transactions ADD COLUMN category_id UUID REFERENCES categories (id);

UPDATE transactions t
SET category_id = c.id
FROM accounts a
         JOIN categories c ON c.user_id = a.user_id
WHERE a.id = t.account_id
  AND t.category IS NOT NULL
  AND lower(c.name) = lower(t.category);

-- 3. The label column is now redundant.
ALTER TABLE transactions DROP COLUMN category;

CREATE INDEX idx_transactions_category ON transactions (category_id);
