\set ON_ERROR_STOP on
DO $$ BEGIN
    IF current_database() !~ '^fintrack_manual_eval_[a-zA-Z0-9_]+$' THEN
        RAISE EXCEPTION 'A fresh disposable fintrack_manual_eval_ database is required';
    END IF;
END $$;
CREATE TABLE transactions (id BIGINT PRIMARY KEY,user_id BIGINT,amount NUMERIC(10,2),
    transaction_hash VARCHAR(64),capture_origin VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    CONSTRAINT chk_transaction_capture_origin CHECK (capture_origin IN ('UNKNOWN','EMAIL','PHONE')));
INSERT INTO transactions VALUES(1,1,12.34,'synthetic-1','EMAIL'),(2,2,99.99,'synthetic-2','UNKNOWN');
CREATE TEMP TABLE before_manual AS SELECT id,to_jsonb(t) AS data FROM transactions t;
\ir 20261028_manual_transactions.sql
\ir 20261028_manual_transactions.sql
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM transactions t JOIN before_manual b USING(id)
        WHERE to_jsonb(t)-'manual_request_fingerprint'-'reconciled_to_id'-'reconciled_at'-'reconciliation_revision' <> b.data
        OR reconciled_to_id IS NOT NULL OR reconciliation_revision<>0 OR manual_request_fingerprint IS NOT NULL) THEN
        RAISE EXCEPTION 'Migration modified financial history or invented manual records';
    END IF;
END $$;
INSERT INTO transactions(id,user_id,amount,transaction_hash,capture_origin,manual_request_fingerprint)
 VALUES(3,1,12.34,'synthetic-manual-1','MANUAL',repeat('a',64)),(4,1,12.34,'synthetic-manual-2','MANUAL',repeat('b',64));
UPDATE transactions SET reconciled_to_id=1,reconciled_at='2026-10-28 12:00',reconciliation_revision=1 WHERE id=3;
DO $$ BEGIN
    BEGIN
        UPDATE transactions SET reconciled_to_id=2 WHERE id=3;
        RAISE EXCEPTION 'Cross-user link accepted';
    EXCEPTION WHEN foreign_key_violation THEN NULL; END;
    BEGIN
        UPDATE transactions SET reconciled_to_id=3 WHERE id=3;
        RAISE EXCEPTION 'Self reconciliation accepted';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        UPDATE transactions SET reconciled_to_id=1,reconciled_at=now() WHERE id=4;
        RAISE EXCEPTION 'Second manual for the same capture accepted';
    EXCEPTION WHEN unique_violation THEN NULL; END;
    BEGIN
        UPDATE transactions SET reconciled_at=NULL WHERE id=3;
        RAISE EXCEPTION 'Incomplete reconciliation accepted';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        UPDATE transactions SET manual_request_fingerprint=NULL WHERE id=3;
        RAISE EXCEPTION 'Manual identity without fingerprint accepted';
    EXCEPTION WHEN check_violation THEN NULL; END;
END $$;
\ir 20261028_manual_transactions.sql
DO $$ BEGIN
    IF (SELECT reconciled_to_id FROM transactions WHERE id=3)<>1 OR (SELECT amount FROM transactions WHERE id=3)<>12.34 THEN
        RAISE EXCEPTION 'Repeated migration lost reconciliation or financial evidence';
    END IF;
END $$;
