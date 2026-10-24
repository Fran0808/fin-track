-- Apply only after deploying the API that no longer maps tags/notes.
-- Back up these columns first if their historical values must be retained.
-- This migration permanently removes tags and personal notes, never movements.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
ALTER TABLE public.transactions
    DROP COLUMN IF EXISTS tags,
    DROP COLUMN IF EXISTS notes;
COMMIT;
