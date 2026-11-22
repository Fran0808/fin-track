BEGIN;

ALTER TABLE public.google_oauth_tokens
    ADD COLUMN IF NOT EXISTS sync_window_end BIGINT,
    ADD COLUMN IF NOT EXISTS sync_page_token TEXT,
    ADD COLUMN IF NOT EXISTS sync_page_size INTEGER,
    ADD COLUMN IF NOT EXISTS sync_retry_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS sync_rate_limit_count INTEGER;

COMMIT;
