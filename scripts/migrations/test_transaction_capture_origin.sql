\set ON_ERROR_STOP on
-- Synthetic regression only; never run against an application database.
DO $$ BEGIN
    IF current_database() !~ '^fintrack_capture_origin_eval_[a-zA-Z0-9_]+$' THEN
        RAISE EXCEPTION 'An empty disposable fintrack_capture_origin_eval_ database is required';
    END IF;
END $$;
CREATE TABLE transactions (
    id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, amount NUMERIC(10,2) NOT NULL,
    flow_type VARCHAR(20) NOT NULL, transaction_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL, UNIQUE (transaction_hash, user_id)
);
INSERT INTO transactions VALUES
    (1,1,12.34,'EXPENSE','shared-synthetic-hash','2026-01-01 12:00'),
    (2,2,12.34,'EXPENSE','shared-synthetic-hash','2026-01-01 12:00');
CREATE TEMP TABLE before_migration AS SELECT id, to_jsonb(t) AS data FROM transactions t;
\ir 20261025_transaction_capture_origin.sql
\ir 20261025_transaction_capture_origin.sql
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM transactions t JOIN before_migration b USING(id)
        WHERE to_jsonb(t) - 'capture_origin' - 'operation_reference' <> b.data
            OR capture_origin <> 'UNKNOWN' OR operation_reference IS NOT NULL) THEN
        RAISE EXCEPTION 'Migration changed history or invented origin evidence';
    END IF;
    BEGIN
        UPDATE transactions SET capture_origin = 'BANK' WHERE id=1;
        RAISE EXCEPTION 'Invalid origin accepted';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        UPDATE transactions SET capture_origin = NULL WHERE id=1;
        RAISE EXCEPTION 'Null origin accepted';
    EXCEPTION WHEN not_null_violation THEN NULL; END;
    BEGIN
        UPDATE transactions SET capture_origin = 'PHONE', operation_reference = 'OP-123' WHERE id=1;
        RAISE EXCEPTION 'Receipt reference without email evidence accepted';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        UPDATE transactions SET capture_origin = 'EMAIL', operation_reference = '<script>' WHERE id=1;
        RAISE EXCEPTION 'Unbounded/free text evidence accepted';
    EXCEPTION WHEN check_violation THEN NULL; END;
END $$;
UPDATE transactions SET capture_origin='EMAIL', operation_reference='OP-123' WHERE id=1;
\ir 20261025_transaction_capture_origin.sql
DO $$ BEGIN
    IF (SELECT operation_reference FROM transactions WHERE id=1) <> 'OP-123'
       OR (SELECT capture_origin FROM transactions WHERE id=2) <> 'UNKNOWN' THEN
        RAISE EXCEPTION 'Rerun lost existing evidence or changed another user';
    END IF;
END $$;
SELECT 'Capture-origin migration preserves history, remains repeatable and validates explicit evidence.' AS result;
