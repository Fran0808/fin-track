\set ON_ERROR_STOP on
-- Synthetic regression only; never run against an application database.
DO $$ BEGIN
    IF current_database() !~ '^fintrack_metadata_eval_[a-zA-Z0-9_]+$' THEN
        RAISE EXCEPTION 'A disposable fintrack_metadata_eval_ database is required';
    END IF;
END $$;
CREATE TABLE public.users (id BIGINT PRIMARY KEY);
CREATE TABLE public.financial_instruments (id BIGINT PRIMARY KEY);
CREATE TABLE public.transactions (
    id BIGINT PRIMARY KEY,
    user_id BIGINT REFERENCES public.users(id),
    financial_instrument_id BIGINT REFERENCES public.financial_instruments(id),
    amount NUMERIC(10,2) NOT NULL,
    category VARCHAR(100),
    transaction_hash VARCHAR(64) NOT NULL,
    tags VARCHAR(255), notes VARCHAR(500),
    UNIQUE (transaction_hash, user_id)
);
INSERT INTO public.users VALUES (1), (2);
INSERT INTO public.financial_instruments VALUES (10);
INSERT INTO public.transactions VALUES
    (1, 1, 10, 12.34, 'Comida', 'synthetic-shared-hash', 'fixture', 'Synthetic note'),
    (2, 2, NULL, 0.26, NULL, 'synthetic-shared-hash', NULL, NULL);
\ir 20261024_remove_transaction_metadata.sql
\ir 20261024_remove_transaction_metadata.sql
DO $$ BEGIN
    IF EXISTS (SELECT FROM information_schema.columns WHERE table_schema = 'public'
        AND table_name = 'transactions' AND column_name IN ('tags', 'notes')) THEN
        RAISE EXCEPTION 'Retired columns still exist';
    END IF;
    IF (SELECT count(*) FROM public.transactions) <> 2 OR NOT EXISTS (
        SELECT FROM public.transactions WHERE id = 1 AND user_id = 1 AND amount = 12.34
          AND financial_instrument_id = 10 AND category = 'Comida'
          AND transaction_hash = 'synthetic-shared-hash') OR NOT EXISTS (
        SELECT FROM public.transactions WHERE id = 2 AND user_id = 2 AND amount = 0.26
          AND financial_instrument_id IS NULL AND category IS NULL) THEN
        RAISE EXCEPTION 'Movement data changed';
    END IF;
    BEGIN
        INSERT INTO public.transactions VALUES (3, 1, NULL, 1.00, NULL, 'synthetic-shared-hash');
        RAISE EXCEPTION 'Per-user idempotency constraint was lost';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO public.transactions VALUES (4, 99, NULL, 1.00, NULL, 'synthetic-foreign-user');
        RAISE EXCEPTION 'Ownership foreign key was lost';
    EXCEPTION WHEN foreign_key_violation THEN NULL;
    END;
END $$;
