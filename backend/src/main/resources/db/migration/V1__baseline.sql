-- Baseline migration. Establishes the Flyway history table so every later schema
-- change (users, accounts, transactions, ...) is a new, versioned, reviewable V*.sql file.
-- Never edit a migration after it has been applied; add a new version instead.
SELECT 1;
