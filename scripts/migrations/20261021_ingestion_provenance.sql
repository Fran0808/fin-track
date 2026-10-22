-- Existing installations: preserve legacy rows without guessing ownership.
-- Confirm the target and back it up before applying this migration.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

ALTER TABLE raw_notification_logs
    ADD COLUMN IF NOT EXISTS user_id BIGINT REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS transaction_id BIGINT REFERENCES transactions(id);
ALTER TABLE processed_email_messages
    ADD COLUMN IF NOT EXISTS user_id BIGINT REFERENCES users(id);

-- No backfill: raw text and opaque message hashes cannot establish legacy ownership.
COMMIT;
