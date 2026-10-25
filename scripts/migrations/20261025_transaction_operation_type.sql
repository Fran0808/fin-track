-- Existing installations: additive metadata only. Never infer historical meaning from a bank/contact name.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

ALTER TABLE transactions ADD COLUMN IF NOT EXISTS operation_type VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN';
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conname = 'chk_transaction_operation_flow' AND conrelid = 'transactions'::regclass) THEN
        ALTER TABLE transactions ADD CONSTRAINT chk_transaction_operation_flow
            CHECK (operation_type <> 'OWN_CARD_PAYMENT' OR flow_type = 'INTERNAL_TRANSFER');
    END IF;
END $$;
COMMIT;
