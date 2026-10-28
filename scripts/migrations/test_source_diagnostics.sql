\set ON_ERROR_STOP on
DO $$ BEGIN
    IF current_database() !~ '^fintrack_sources_eval_[a-zA-Z0-9_]+$' THEN
        RAISE EXCEPTION 'A fresh disposable fintrack_sources_eval_ database is required';
    END IF;
END $$;
CREATE TABLE users (id BIGINT PRIMARY KEY, email TEXT, device_pairing_token TEXT);
CREATE TABLE google_oauth_tokens (id BIGINT PRIMARY KEY, user_id BIGINT REFERENCES users(id),
    access_token TEXT, last_successful_sync_at TIMESTAMPTZ, last_sync_failed BOOLEAN);
INSERT INTO users VALUES (1,'owner@example.test','synthetic-device'), (2,'other@example.test','synthetic-other');
INSERT INTO google_oauth_tokens VALUES (1,1,'synthetic-access','2026-01-01 12:00Z',true);
CREATE TEMP TABLE before_users AS SELECT id,to_jsonb(u) AS data FROM users u;
CREATE TEMP TABLE before_tokens AS SELECT id,to_jsonb(t) AS data FROM google_oauth_tokens t;
\ir 20261027_source_diagnostics.sql
\ir 20261027_source_diagnostics.sql
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM users u JOIN before_users b USING(id)
        WHERE to_jsonb(u)-'last_phone_received_at' <> b.data OR last_phone_received_at IS NOT NULL)
        OR EXISTS (SELECT 1 FROM google_oauth_tokens t JOIN before_tokens b USING(id)
        WHERE to_jsonb(t)-'last_sync_attempt_at'-'last_sync_failure_code' <> b.data
        OR last_sync_attempt_at IS NOT NULL OR last_sync_failure_code IS NOT NULL) THEN
        RAISE EXCEPTION 'Migration changed existing data or invented diagnostics';
    END IF;
    BEGIN
        UPDATE google_oauth_tokens SET last_sync_failure_code='provider private response';
        RAISE EXCEPTION 'Arbitrary failure text accepted';
    EXCEPTION WHEN check_violation THEN NULL; END;
END $$;
UPDATE users SET last_phone_received_at='2026-10-27 12:00Z' WHERE id=1;
UPDATE google_oauth_tokens SET last_sync_failure_code='TEMPORARY',last_sync_attempt_at='2026-10-27 12:00Z';
\ir 20261027_source_diagnostics.sql
DO $$ BEGIN
    IF (SELECT last_phone_received_at FROM users WHERE id=1) <> '2026-10-27 12:00Z'::timestamptz
        OR (SELECT last_sync_failure_code FROM google_oauth_tokens WHERE id=1) <> 'TEMPORARY'
        OR (SELECT last_phone_received_at FROM users WHERE id=2) IS NOT NULL THEN
        RAISE EXCEPTION 'Repeated migration changed explicit diagnostics';
    END IF;
END $$;
