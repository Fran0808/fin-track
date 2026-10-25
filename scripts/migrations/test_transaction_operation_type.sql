\set ON_ERROR_STOP on
-- Synthetic regression only; never run against an application database.
DO $$ BEGIN
    IF current_database() !~ '^fintrack_operations_eval_[a-zA-Z0-9_]+$' THEN
        RAISE EXCEPTION 'An empty disposable fintrack_operations_eval_ database is required';
    END IF;
END $$;
CREATE TABLE transactions (
    id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, financial_instrument_id BIGINT,
    amount NUMERIC(10,2) NOT NULL, flow_type VARCHAR(20) NOT NULL,
    contact_name VARCHAR(150) NOT NULL, channel VARCHAR(50) NOT NULL,
    card_last4 VARCHAR(10), transaction_date TIMESTAMP NOT NULL,
    category VARCHAR(100), transaction_hash VARCHAR(64) NOT NULL, created_at TIMESTAMP NOT NULL,
    UNIQUE (transaction_hash, user_id)
);
INSERT INTO transactions VALUES
    (1, 1, 10, 81.25, 'EXPENSE', 'Pago Tarjeta Crédito BCP', 'TARJETA_CREDITO_BCP', '1111', '2026-01-01 12:00', 'Example', 'shared-synthetic-hash', '2026-01-01 12:00'),
    (2, 2, NULL, 81.25, 'INTERNAL_TRANSFER', 'Pago Tarjeta Crédito BCP', 'TARJETA_CREDITO_BCP', '1111', '2026-01-01 12:00', NULL, 'shared-synthetic-hash', '2026-01-01 12:00');
CREATE TEMP TABLE before_migration AS SELECT id, to_jsonb(t) AS data FROM transactions t;
\ir 20261025_transaction_operation_type.sql
\ir 20261025_transaction_operation_type.sql
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM transactions t JOIN before_migration b USING (id)
               WHERE to_jsonb(t) - 'operation_type' <> b.data OR operation_type <> 'UNKNOWN') THEN
        RAISE EXCEPTION 'Migration changed historical values or guessed an operation type';
    END IF;
    BEGIN
        UPDATE transactions SET operation_type = 'OWN_CARD_PAYMENT' WHERE id = 1;
        RAISE EXCEPTION 'An own-card payment must not retain expense flow';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        UPDATE transactions SET operation_type = NULL WHERE id = 1;
        RAISE EXCEPTION 'Operation metadata must be non-null';
    EXCEPTION WHEN not_null_violation THEN NULL; END;
    BEGIN
        UPDATE transactions SET operation_type = 'OWN_CARD_PAYMENT', flow_type = 'INCOME' WHERE id = 2;
        RAISE EXCEPTION 'An own-card payment must not become income';
    EXCEPTION WHEN check_violation THEN NULL; END;
END $$;
UPDATE transactions SET operation_type = 'OWN_CARD_PAYMENT' WHERE id = 2;
\ir 20261025_transaction_operation_type.sql
DO $$ BEGIN
    IF (SELECT operation_type FROM transactions WHERE id = 2) <> 'OWN_CARD_PAYMENT' THEN
        RAISE EXCEPTION 'Rerun lost an explicitly assigned operation';
    END IF;
END $$;
SELECT 'Operation migration preserves history, defaults unknown, is repeatable and enforces neutral repayments.' AS result;
