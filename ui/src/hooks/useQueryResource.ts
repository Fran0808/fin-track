import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

const CACHED_QUERY_LIMIT = 6;

interface QueryCacheKey {
  userId: number;
  period: string;
}

// Each effect owns its requests. A replaced query cannot publish data or errors.
export function useQueryResource<T>(load: () => Promise<T>, fallbackError: string, cacheKey?: QueryCacheKey) {
  const cacheUserId = cacheKey?.userId;
  const cachePeriod = cacheKey?.period;
  const query = useMemo(() => ({ load, fallbackError, cacheUserId, cachePeriod }), [load, fallbackError, cacheUserId, cachePeriod]);
  const [result, setResult] = useState<{
    query: typeof query;
    data: T | null;
    error: string | null;
    loading: boolean;
  } | null>(null);
  const [cache, setCache] = useState<{ userId: number; periods: Map<string, T> } | null>(null);
  const reloadRef = useRef<(silent?: boolean) => Promise<void>>(async () => {});

  useEffect(() => {
    let active = true;
    let sequence = 0;
    const { load, fallbackError, cacheUserId, cachePeriod } = query;
    const reload = async (silent = false) => {
      if (!active) return;
      const request = ++sequence;
      setResult(previous => ({
        query,
        data: previous?.query === query ? previous.data : null,
        error: previous?.query === query ? previous.error : null,
        loading: !silent || !previous?.data,
      }));
      try {
        const data = await load();
        if (!active || request !== sequence) return;
        setResult({ query, data, error: null, loading: false });
        if (cacheUserId !== undefined && cachePeriod !== undefined) {
          setCache(previous => {
            const periods = previous?.userId === cacheUserId ? new Map(previous.periods) : new Map<string, T>();
            periods.delete(cachePeriod);
            periods.set(cachePeriod, data);
            if (periods.size > CACHED_QUERY_LIMIT) {
              const oldest = periods.keys().next();
              if (!oldest.done) periods.delete(oldest.value);
            }
            return { userId: cacheUserId, periods };
          });
        }
      } catch (error) {
        if (active && request === sequence) setResult(previous => ({
          query,
          data: previous?.query === query ? previous.data : null,
          error: error instanceof Error ? error.message : fallbackError,
          loading: false,
        }));
      }
    };
    reloadRef.current = reload;
    void reload();
    return () => { active = false; };
  }, [query]);

  const refresh = useCallback((silent = false) => reloadRef.current(silent), []);
  const clearCache = useCallback(() => setCache(null), []);
  const current = result?.query === query ? result : null;
  const cachedData = cache && cache.userId === cacheUserId && cachePeriod !== undefined
    ? cache.periods.get(cachePeriod) : undefined;
  const data = current?.data ?? cachedData ?? null;
  const loading = (current?.loading ?? true) && (cacheKey === undefined || data === null);
  return { data, error: current?.error ?? null, loading, refresh, clearCache };
}
