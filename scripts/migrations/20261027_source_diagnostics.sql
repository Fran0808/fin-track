BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

ALTER TABLE users ADD COLUMN IF NOT EXISTS last_phone_received_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE google_oauth_tokens ADD COLUMN IF NOT EXISTS last_sync_attempt_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE google_oauth_tokens ADD COLUMN IF NOT EXISTS last_sync_failure_code VARCHAR(32);
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'google_oauth_tokens'::regclass
        AND conname = 'chk_email_sync_failure') THEN
        ALTER TABLE google_oauth_tokens ADD CONSTRAINT chk_email_sync_failure CHECK (
            last_sync_failure_code IS NULL OR last_sync_failure_code IN
            ('AUTH_REQUIRED', 'ACCESS_DENIED', 'RATE_LIMITED', 'TEMPORARY', 'PROCESSING', 'UNKNOWN'));
    END IF;
END $$;
COMMIT;
