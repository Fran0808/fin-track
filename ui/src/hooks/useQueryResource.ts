import { useCallback, useEffect, useRef, useState } from 'react';

// Each effect owns its requests. A replaced query cannot publish data or errors.
export function useQueryResource<T>(load: () => Promise<T>, fallbackError: string) {
  const [result, setResult] = useState<{
    query: typeof load;
    data: T | null;
    error: string | null;
    loading: boolean;
  } | null>(null);
  const reloadRef = useRef<(silent?: boolean) => Promise<void>>(async () => {});

  useEffect(() => {
    let active = true;
    let sequence = 0;
    const reload = async (silent = false) => {
      if (!active) return;
      const request = ++sequence;
      setResult(previous => ({
        query: load,
        data: previous?.query === load ? previous.data : null,
        error: previous?.query === load ? previous.error : null,
        loading: !silent || !previous?.data,
      }));
      try {
        const data = await load();
        if (active && request === sequence) setResult({ query: load, data, error: null, loading: false });
      } catch (error) {
        if (active && request === sequence) setResult(previous => ({
          query: load,
          data: previous?.query === load ? previous.data : null,
          error: error instanceof Error ? error.message : fallbackError,
          loading: false,
        }));
      }
    };
    reloadRef.current = reload;
    void reload();
    return () => { active = false; };
  }, [load, fallbackError]);

  const refresh = useCallback((silent = false) => reloadRef.current(silent), []);
  const current = result?.query === load ? result : null;
  return { data: current?.data ?? null, error: current?.error ?? null, loading: current?.loading ?? true, refresh };
}
