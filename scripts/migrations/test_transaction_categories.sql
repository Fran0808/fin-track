\set ON_ERROR_STOP on
-- Run only in a disposable database, never against financial data.
CREATE TABLE public.transactions (
    id bigint PRIMARY KEY,
    amount numeric(10,2) NOT NULL,
    category varchar(50),
    CONSTRAINT transactions_category_check CHECK (category IN ('FOOD', 'OTHER'))
);
INSERT INTO public.transactions VALUES (1, 2.30, 'FOOD');
DO $$ BEGIN
    BEGIN
        UPDATE public.transactions SET category = 'Comida y bebidas' WHERE id = 1;
        RAISE EXCEPTION 'Expected the legacy constraint to reject the category';
    EXCEPTION WHEN check_violation THEN
        NULL;
    END;
END $$;

\ir 20261008_transaction_categories.sql
\ir 20261008_transaction_categories.sql

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM public.transactions WHERE id = 1 AND amount = 2.30 AND category = 'FOOD') THEN
        RAISE EXCEPTION 'Migration changed existing financial values';
    END IF;
    UPDATE public.transactions SET category = 'Comida y bebidas' WHERE id = 1;
    INSERT INTO public.transactions VALUES (2, 1.00, repeat('a', 100));
    INSERT INTO public.transactions VALUES (3, 1.00, NULL);
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'public.transactions'::regclass
            AND conname = 'transactions_category_check') THEN
        RAISE EXCEPTION 'Legacy constraint still exists';
    END IF;
END $$;
