\set ON_ERROR_STOP on
-- Synthetic regression only; never run against an application database.
DO $$ BEGIN
    IF current_database() !~ '^fintrack_remove_instruments_eval_[a-zA-Z0-9_]+$' THEN
        RAISE EXCEPTION 'A disposable fintrack_remove_instruments_eval_ database is required';
    END IF;
END $$;
CREATE TABLE public.users (id BIGINT PRIMARY KEY);
CREATE TABLE public.financial_instruments (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES public.users(id),
    alias VARCHAR(100) NOT NULL,
    linked_account_id BIGINT REFERENCES public.financial_instruments(id)
);
CREATE TABLE public.transactions (
    id BIGINT PRIMARY KEY,
    user_id BIGINT REFERENCES public.users(id),
    financial_instrument_id BIGINT REFERENCES public.financial_instruments(id),
    amount NUMERIC(10,2) NOT NULL,
    flow_type VARCHAR(20) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    card_last4 VARCHAR(10),
    category VARCHAR(100),
    transaction_date TIMESTAMP NOT NULL,
    transaction_hash VARCHAR(64) NOT NULL,
    UNIQUE (transaction_hash, user_id)
);
CREATE INDEX idx_tx_user_instrument_date ON public.transactions(user_id, financial_instrument_id, transaction_date);
INSERT INTO public.users VALUES (1), (2);
INSERT INTO public.financial_instruments VALUES (10, 1, 'Synthetic account', NULL), (11, 1, 'Synthetic card', 10);
INSERT INTO public.transactions VALUES
    (1, 1, 11, 12.34, 'EXPENSE', 'TARJETA_CREDITO_BCP', '3127', 'Comida', '2026-10-01 12:00:00', 'synthetic-shared-hash'),
    (2, 2, NULL, 0.26, 'INCOME', 'YAPE', NULL, NULL, '2026-10-02 12:00:00', 'synthetic-shared-hash');
\ir 20261028_remove_financial_instruments.sql
\ir 20261028_remove_financial_instruments.sql
DO $$ BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_schema = 'public'
        AND table_name = 'financial_instruments')
       OR EXISTS (SELECT FROM information_schema.columns WHERE table_schema = 'public'
        AND table_name = 'transactions' AND column_name = 'financial_instrument_id') THEN
        RAISE EXCEPTION 'Retired financial_instruments schema still exists';
    END IF;
    IF (SELECT count(*) FROM public.transactions) <> 2 OR NOT EXISTS (
        SELECT FROM public.transactions WHERE id = 1 AND user_id = 1 AND amount = 12.34
          AND flow_type = 'EXPENSE' AND channel = 'TARJETA_CREDITO_BCP' AND card_last4 = '3127'
          AND category = 'Comida' AND transaction_hash = 'synthetic-shared-hash') OR NOT EXISTS (
        SELECT FROM public.transactions WHERE id = 2 AND user_id = 2 AND amount = 0.26
          AND flow_type = 'INCOME' AND channel = 'YAPE' AND card_last4 IS NULL AND category IS NULL) THEN
        RAISE EXCEPTION 'Movement data changed';
    END IF;
    BEGIN
        INSERT INTO public.transactions VALUES (3, 1, 1.00, 'EXPENSE', 'YAPE', NULL, NULL, '2026-10-03 12:00:00', 'synthetic-shared-hash');
        RAISE EXCEPTION 'Per-user idempotency constraint was lost';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO public.transactions VALUES (4, 99, 1.00, 'EXPENSE', 'YAPE', NULL, NULL, '2026-10-03 12:00:00', 'synthetic-foreign-user');
        RAISE EXCEPTION 'Ownership foreign key was lost';
    EXCEPTION WHEN foreign_key_violation THEN NULL;
    END;
END $$;
