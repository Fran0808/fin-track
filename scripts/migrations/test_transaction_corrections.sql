\set ON_ERROR_STOP on
DO $$ BEGIN
    IF current_database() !~ '^fintrack_corrections_eval_[a-zA-Z0-9_]+$' OR to_regclass('public.transactions') IS NOT NULL THEN
        RAISE EXCEPTION 'A fresh disposable fintrack_corrections_eval_ database is required';
    END IF;
END $$;
CREATE TABLE users(id BIGINT PRIMARY KEY);
CREATE TABLE transactions(id BIGINT PRIMARY KEY,user_id BIGINT,amount NUMERIC(10,2));
INSERT INTO users VALUES(1),(2);
INSERT INTO transactions VALUES(1,1,150.00),(2,2,99.99);
\ir 20261029_transaction_corrections.sql
\ir 20261029_transaction_corrections.sql
INSERT INTO transaction_corrections(transaction_id,user_id,request_id,expected_revision,reason,before_amount,after_amount,
    before_date,after_date,before_flow,after_flow,before_operation,after_operation,created_at)
VALUES(1,1,'synthetic-request',0,'Synthetic correction',150.00,15.00,'2026-10-01','2026-09-30','EXPENSE','INCOME','UNKNOWN','UNKNOWN',now());
DO $$ BEGIN
    IF (SELECT amount FROM transactions WHERE id=1)<>150.00 OR (SELECT count(*) FROM transactions)<>2 THEN
        RAISE EXCEPTION 'Migration changed existing financial data';
    END IF;
    BEGIN
        UPDATE transaction_corrections SET user_id=2 WHERE transaction_id=1;
        RAISE EXCEPTION 'Cross-user correction accepted';
    EXCEPTION WHEN foreign_key_violation THEN NULL; END;
    BEGIN
        UPDATE transaction_corrections SET after_amount=0;
        RAISE EXCEPTION 'Invalid amount accepted';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        UPDATE transaction_corrections SET after_operation='OWN_CARD_PAYMENT';
        RAISE EXCEPTION 'Invalid own-payment flow accepted';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        UPDATE transaction_corrections SET reason='   ';
        RAISE EXCEPTION 'Blank reason accepted';
    EXCEPTION WHEN check_violation THEN NULL; END;
END $$;
