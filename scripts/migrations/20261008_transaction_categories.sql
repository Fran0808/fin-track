BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

-- Categories now come from each user's catalog rather than a fixed enum.
ALTER TABLE public.transactions DROP CONSTRAINT IF EXISTS transactions_category_check;
ALTER TABLE public.transactions ALTER COLUMN category TYPE varchar(100);

COMMIT;
