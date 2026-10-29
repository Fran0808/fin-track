BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS manual_request_fingerprint VARCHAR(64);
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS reconciled_to_id BIGINT;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS reconciled_at TIMESTAMP;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS reconciliation_revision INTEGER NOT NULL DEFAULT 0;
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS chk_transaction_capture_origin;
ALTER TABLE transactions ADD CONSTRAINT chk_transaction_capture_origin CHECK (capture_origin IN ('UNKNOWN','EMAIL','PHONE','MANUAL'));
CREATE UNIQUE INDEX IF NOT EXISTS idx_tx_id_user ON transactions(id,user_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_tx_reconciled_to ON transactions(reconciled_to_id);
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='transactions'::regclass AND conname='fk_tx_reconciled_owner') THEN
        ALTER TABLE transactions ADD CONSTRAINT fk_tx_reconciled_owner FOREIGN KEY (reconciled_to_id,user_id) REFERENCES transactions(id,user_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='transactions'::regclass AND conname='chk_tx_manual_evidence') THEN
        ALTER TABLE transactions ADD CONSTRAINT chk_tx_manual_evidence CHECK (
            (capture_origin='MANUAL' AND manual_request_fingerprint IS NOT NULL AND manual_request_fingerprint ~ '^[a-f0-9]{64}$')
            OR (capture_origin<>'MANUAL' AND manual_request_fingerprint IS NULL));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='transactions'::regclass AND conname='chk_tx_reconciliation') THEN
        ALTER TABLE transactions ADD CONSTRAINT chk_tx_reconciliation CHECK (
            reconciliation_revision >= 0 AND
            ((reconciled_to_id IS NULL AND reconciled_at IS NULL) OR
             (capture_origin='MANUAL' AND reconciled_to_id IS NOT NULL AND reconciled_to_id<>id AND reconciled_at IS NOT NULL)));
    END IF;
END $$;
COMMIT;
