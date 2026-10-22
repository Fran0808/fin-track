-- Synthetic regression only, in a new disposable database with the required name.
\set ON_ERROR_STOP on
DO $$
BEGIN
    IF current_database() !~ '^fintrack_provenance_eval_[a-z0-9_]+$'
       OR EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public') THEN
        RAISE EXCEPTION 'An empty disposable fintrack_provenance_eval_ database is required';
    END IF;
END $$;

CREATE TABLE users (id BIGINT PRIMARY KEY);
CREATE TABLE transactions (id BIGINT PRIMARY KEY, user_id BIGINT REFERENCES users(id));
CREATE TABLE raw_notification_logs (id BIGINT PRIMARY KEY, raw_text TEXT NOT NULL);
CREATE TABLE processed_email_messages (message_id VARCHAR(100) PRIMARY KEY);
INSERT INTO users VALUES (1), (2);
INSERT INTO transactions VALUES (10, 1);
INSERT INTO raw_notification_logs VALUES (1, 'Synthetic legacy evidence');
INSERT INTO processed_email_messages VALUES ('synthetic-legacy-marker');

\ir 20261021_ingestion_provenance.sql
\ir 20261021_ingestion_provenance.sql

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM raw_notification_logs WHERE id = 1
        AND raw_text = 'Synthetic legacy evidence' AND user_id IS NULL AND transaction_id IS NULL)
        OR NOT EXISTS (SELECT 1 FROM processed_email_messages
            WHERE message_id = 'synthetic-legacy-marker' AND user_id IS NULL) THEN
        RAISE EXCEPTION 'Legacy evidence or unknown ownership changed';
    END IF;
    BEGIN
        INSERT INTO raw_notification_logs VALUES (2, 'Synthetic invalid owner', 999, 10);
        RAISE EXCEPTION 'Unknown raw evidence owner was accepted';
    EXCEPTION WHEN foreign_key_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO raw_notification_logs VALUES (2, 'Synthetic invalid movement', 1, 999);
        RAISE EXCEPTION 'Unknown movement was accepted';
    EXCEPTION WHEN foreign_key_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO processed_email_messages VALUES ('synthetic-invalid-owner', 999);
        RAISE EXCEPTION 'Unknown email marker owner was accepted';
    EXCEPTION WHEN foreign_key_violation THEN NULL;
    END;
END $$;
INSERT INTO raw_notification_logs VALUES (2, 'Synthetic new evidence', 1, 10);
INSERT INTO processed_email_messages VALUES ('synthetic-new-marker', 2);
SELECT 'Ingestion provenance migration preserves legacy rows, is repeatable and enforces foreign keys.' AS result;
