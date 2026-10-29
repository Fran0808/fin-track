-- Apply only after deploying the API that no longer maps financial_instruments.
-- Back up financial_instruments and transactions.financial_instrument_id first if needed.
-- This migration permanently removes the financial_instruments catalog and transaction link, never movements.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
DROP INDEX IF EXISTS public.idx_tx_user_instrument_date;
ALTER TABLE public.transactions
    DROP COLUMN IF EXISTS financial_instrument_id;
DROP TABLE IF EXISTS public.financial_instruments;
COMMIT;
