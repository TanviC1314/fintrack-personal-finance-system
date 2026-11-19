CREATE TABLE categories (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id),
    name       VARCHAR(50)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- A user's category names are unique ignoring case ("Food" and "food" are the same category).
CREATE UNIQUE INDEX uq_categories_user_name ON categories (user_id, lower(name));
