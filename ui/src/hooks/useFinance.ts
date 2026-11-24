import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { api } from '../services/api';
import type { TransactionFilterParams } from '../services/api';
import type { FinancialSummary, PeriodAnalytics, Transaction, PageResponse, TransactionPageResponse, EmailSyncResponse, GoogleAuthStatus } from '../types';
import { useQueryResource } from './useQueryResource';

const NON_RECOVERABLE_SYNC_CODES = new Set(['AUTH_REQUIRED', 'ACCESS_DENIED', 'RATE_LIMITED']);
const EMPTY_BATCH_DELAY_MS = 1_000;
const PROCESSED_BATCH_DELAY_MS = 15_000;

function waitForNextEmailBatch(delay: number, signal: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    signal.throwIfAborted();
    const onAbort = () => {
      clearTimeout(timer);
      reject(signal.reason);
    };
    const timer = setTimeout(() => {
      signal.removeEventListener('abort', onAbort);
      resolve();
    }, delay);
    signal.addEventListener('abort', onAbort, { once: true });
  });
}

function getPeriodDateRange(year: number, month: number) {
  const pad = (n: number) => String(n).padStart(2, '0');
  const lastDay = new Date(year, month, 0).getDate();
  return {
    startDate: `${year}-${pad(month)}-01T00:00:00`,
    endDate: `${year}-${pad(month)}-${pad(lastDay)}T23:59:59.999999`,
  };
}

export function useFinance(userId: number) {
  const [isSyncing, setIsSyncing] = useState<boolean>(false);
  const [syncBatches, setSyncBatches] = useState(0);
  const [syncStatus, setSyncStatus] = useState<GoogleAuthStatus | null>(null);
  const [syncStatusUnavailable, setSyncStatusUnavailable] = useState(false);
  const [syncError, setSyncError] = useState<string | null>(null);

  const [selectedPeriod, setSelectedPeriodState] = useState<{ year: number; month: number }>({
    year: new Date().getFullYear(),
    month: new Date().getMonth() + 1,
  });

  const [filters, setFilters] = useState<TransactionFilterParams>(() => ({
    page: 0,
    size: 10,
    flowType: '',
    search: '',
    ...(new URLSearchParams(window.location.search).get('vista') === 'pendientes'
      ? { pendingReason: 'ALL' as const, allHistory: true } : {}),
  }));

  const setSelectedPeriod = (period: { year: number; month: number }) => {
    setSelectedPeriodState(period);
    setFilters((prev) => ({ ...prev, allHistory: false, startDate: undefined, endDate: undefined, page: 0 }));
  };

  const transactionFilters = useMemo(() => ({
    ...filters,
    ...(filters.allHistory ? { startDate: undefined, endDate: undefined }
      : filters.startDate && filters.endDate ? {} : getPeriodDateRange(selectedPeriod.year, selectedPeriod.month)),
  }), [filters, selectedPeriod]);

  const summaryQuery = useCallback(() => userId ? api.getFinancialSummary() : Promise.reject(new Error('Sesión no disponible')), [userId]);
  const analyticsQuery = useCallback(() => userId ? api.getPeriodAnalytics(selectedPeriod.year, selectedPeriod.month) : Promise.reject(new Error('Sesión no disponible')), [selectedPeriod, userId]);
  const transactionsQuery = useCallback(() => userId ? api.getTransactions(transactionFilters) : Promise.reject(new Error('Sesión no disponible')), [transactionFilters, userId]);
  const recentQuery = useCallback(() => userId ? api.getTransactions({
    ...getPeriodDateRange(selectedPeriod.year, selectedPeriod.month), page: 0, size: 5,
  }) : Promise.reject(new Error('Sesión no disponible')), [selectedPeriod, userId]);
  const periodCacheKey = userId ? { userId, period: `${selectedPeriod.year}-${selectedPeriod.month}` } : undefined;
  const summaryResource = useQueryResource<FinancialSummary>(summaryQuery, 'Error al cargar el resumen');
  const analyticsResource = useQueryResource<PeriodAnalytics>(analyticsQuery, 'Error al cargar analítica financiera', periodCacheKey);
  const transactionResource = useQueryResource<TransactionPageResponse>(transactionsQuery, 'Error al cargar transacciones');
  useEffect(() => {
    const page = transactionResource.data;
    if (page && page.number > 0 && page.number >= page.totalPages) {
      setFilters(current => ({ ...current, page: Math.max(0, page.totalPages - 1) }));
    }
  }, [transactionResource.data]);
  const recentResource = useQueryResource<PageResponse<Transaction>>(recentQuery, 'Error al cargar movimientos recientes', periodCacheKey);
  const { refresh: loadSummary } = summaryResource;
  const { refresh: loadAnalytics, clearCache: clearAnalyticsCache } = analyticsResource;
  const { refresh: loadTransactions } = transactionResource;
  const { refresh: loadRecent, clearCache: clearRecentCache } = recentResource;
  const clearPeriodCache = useCallback(() => {
    clearAnalyticsCache();
    clearRecentCache();
  }, [clearAnalyticsCache, clearRecentCache]);
  const error = [syncError, summaryResource.error, analyticsResource.error, transactionResource.error, recentResource.error]
    .filter((message, index, messages) => message && messages.indexOf(message) === index).join(' · ') || null;

  const autoRecoveryUserRef = useRef<number | null>(null);
  const activeEmailSync = useRef<AbortController | null>(null);

  useEffect(() => () => {
    activeEmailSync.current?.abort();
    activeEmailSync.current = null;
  }, [userId]);

  const loadSyncStatus = useCallback(async (): Promise<GoogleAuthStatus | null> => {
    if (!userId) {
      setSyncStatus(null);
      setSyncStatusUnavailable(false);
      return null;
    }
    try {
      const status = await api.getGoogleAuthStatus();
      setSyncStatus(status);
      setSyncStatusUnavailable(false);
      return status;
    } catch {
      setSyncStatusUnavailable(true);
      return null;
    }
  }, [userId]);

  useEffect(() => {
    let cancelled = false;
    let retryTimer: ReturnType<typeof setTimeout> | null = null;

    const initSyncStatus = async () => {
      const status = await loadSyncStatus();
      if (cancelled || !userId) return;

      if (!status) {
        retryTimer = setTimeout(() => {
          if (!cancelled) void loadSyncStatus();
        }, 3000);
        return;
      }

      const canAutoRecover =
        status.connected &&
        status.lastSyncFailed &&
        !NON_RECOVERABLE_SYNC_CODES.has(status.lastSyncFailureCode ?? '');

      if (canAutoRecover && autoRecoveryUserRef.current !== userId && !activeEmailSync.current) {
        autoRecoveryUserRef.current = userId;
        const controller = new AbortController();
        activeEmailSync.current = controller;
        try {
          setSyncBatches(0);
          setIsSyncing(true);
          await api.syncEmails(controller.signal);
          if (!cancelled) {
            clearPeriodCache();
            await Promise.all([loadSummary(true), loadAnalytics(true), loadTransactions(true), loadRecent(true)]);
          }
        } catch {
          // Silent background recovery; status will reflect latest state below
        } finally {
          if (!cancelled) await loadSyncStatus();
          if (activeEmailSync.current === controller) {
            activeEmailSync.current = null;
            setIsSyncing(false);
          }
        }
      }
    };

    void initSyncStatus();
    return () => {
      cancelled = true;
      if (retryTimer) clearTimeout(retryTimer);
    };
  }, [userId, loadSyncStatus, loadSummary, loadAnalytics, loadTransactions, loadRecent, clearPeriodCache]);

  useEffect(() => {
    const interval = setInterval(() => {
      if (!isSyncing) {
        // Silent background polling: keeps data up to date without screen flickering
        loadSummary(true);
        loadAnalytics(true);
        loadTransactions(true);
        loadRecent(true);
        if (userId) loadSyncStatus();
      }
    }, 45000);

    return () => clearInterval(interval);
  }, [loadSummary, loadAnalytics, loadTransactions, loadRecent, loadSyncStatus, isSyncing, userId]);

  const triggerEmailSync = async (): Promise<Pick<EmailSyncResponse, 'status' | 'scannedCount' | 'savedCount'>> => {
    if (!userId) throw new Error('Sesión no disponible');
    if (activeEmailSync.current) throw new Error('Ya hay una sincronización en curso.');
    const controller = new AbortController();
    activeEmailSync.current = controller;
    const { signal } = controller;
    try {
      setIsSyncing(true);
      setSyncBatches(0);
      setSyncError(null);
      let scannedCount = 0;
      let savedCount = 0;
      for (;;) {
        signal.throwIfAborted();
        const batch = await api.syncEmails(signal);
        signal.throwIfAborted();
        scannedCount += batch.scannedCount;
        savedCount += batch.savedCount;
        if (batch.status !== 'CONTINUING') return { status: batch.status, scannedCount, savedCount };
        setSyncBatches(count => count + 1);
        await waitForNextEmailBatch(
          batch.scannedCount > 0 ? PROCESSED_BATCH_DELAY_MS : EMPTY_BATCH_DELAY_MS, signal,
        );
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Error al sincronizar correos';
      if (!signal.aborted) setSyncError(msg);
      throw err;
    } finally {
      if (!signal.aborted) {
        clearPeriodCache();
        await Promise.all([loadSyncStatus(), loadSummary(true), loadAnalytics(true), loadTransactions(true), loadRecent(true)]);
      }
      if (activeEmailSync.current === controller) {
        activeEmailSync.current = null;
        setIsSyncing(false);
      }
    }
  };

  const handlePageChange = (newPage: number) => {
    setFilters((prev) => ({ ...prev, page: newPage }));
  };

  const handleFilterChange = (newFilters: Partial<TransactionFilterParams>) => {
    setFilters((prev) => ({ ...prev, ...newFilters, page: 0 }));
  };

  const showMerchantTransactions = (merchantName: string, startDate: string, endDate: string) => {
    setFilters({ page: 0, size: 10, flowType: 'EXPENSE', search: '', merchantName,
      startDate, endDate, allHistory: false });
  };

  const showCategoryTransactions = (category: string | null, startDate: string, endDate: string) => {
    setFilters({ page: 0, size: 10, flowType: 'EXPENSE', search: '', categoryGroup: category ?? '',
      startDate, endDate, allHistory: false });
  };

  const showChannelTransactions = (channel: string) => {
    setFilters({ page: 0, size: 10, flowType: 'EXPENSE', search: '', channel, allHistory: false });
  };

  const showDayTransactions = (dateIso: string) => {
    const datePart = dateIso.slice(0, 10);
    setFilters({ page: 0, size: 10, flowType: 'EXPENSE', search: '',
      startDate: `${datePart}T00:00:00`, endDate: `${datePart}T23:59:59.999999`, allHistory: false });
  };

  return {
    summary: summaryResource.data,
    periodAnalytics: analyticsResource.data,
    transactionsPage: transactionResource.data,
    recentTransactions: recentResource.data?.content ?? [],
    recentUnavailable: Boolean(recentResource.error && !recentResource.data),
    loadingRecent: recentResource.loading,
    loadingSummary: analyticsResource.loading,
    loadingTransactions: transactionResource.loading,
    isSyncing,
    syncBatches,
    syncStatus,
    syncStatusUnavailable,
    error,
    filters,
    transactionFilters,
    selectedPeriod,
    setSelectedPeriod,
    handlePageChange,
    handleFilterChange,
    showMerchantTransactions,
    showCategoryTransactions,
    showDayTransactions,
    showChannelTransactions,
    triggerEmailSync,
    refreshSyncStatus: async () => {
      await loadSyncStatus();
    },
    refreshAll: async () => {
      clearPeriodCache();
      await Promise.all([loadSummary(), loadAnalytics(), loadTransactions(), loadRecent()]);
    },
  };
}
