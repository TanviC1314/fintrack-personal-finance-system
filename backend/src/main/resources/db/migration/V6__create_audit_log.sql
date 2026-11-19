CREATE TABLE audit_log (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id),
    entity_type VARCHAR(30) NOT NULL,
    entity_id   UUID        NOT NULL,
    action      VARCHAR(30) NOT NULL,
    -- Facts needed to understand the event later (amounts, names), not a copy of the whole row.
    details     JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_audit_log_entity_type CHECK (entity_type IN ('ACCOUNT', 'TRANSACTION', 'TRANSFER')),
    CONSTRAINT ck_audit_log_action CHECK (action IN ('CREATED', 'UPDATED', 'REVERSED', 'RENAMED', 'ARCHIVED'))
);

CREATE INDEX idx_audit_log_user_created ON audit_log (user_id, created_at DESC);
CREATE INDEX idx_audit_log_entity ON audit_log (entity_type, entity_id);

-- The audit trail is append-only: not even application bugs can rewrite history.
CREATE FUNCTION audit_log_reject_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_append_only
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_reject_change();
