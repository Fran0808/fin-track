-- Add first-capture evidence without guessing historical origins or changing money/identity.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS capture_origin VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS operation_reference VARCHAR(100);
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'transactions'::regclass AND conname = 'chk_transaction_capture_origin') THEN
        ALTER TABLE transactions ADD CONSTRAINT chk_transaction_capture_origin CHECK (capture_origin IN ('UNKNOWN', 'EMAIL', 'PHONE'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'transactions'::regclass AND conname = 'chk_transaction_operation_reference') THEN
        ALTER TABLE transactions ADD CONSTRAINT chk_transaction_operation_reference CHECK (
            operation_reference IS NULL OR (capture_origin = 'EMAIL' AND operation_reference ~ '^[A-Za-z0-9][A-Za-z0-9._/-]{0,99}$'));
    END IF;
END $$;
COMMIT;
