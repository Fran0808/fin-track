import { useState, useEffect, useCallback, useMemo } from 'react';
import { api } from '../services/api';
import type { TransactionFilterParams } from '../services/api';
import type { FinancialSummary, PeriodAnalytics, Transaction, PageResponse, EmailSyncResponse, GoogleAuthStatus } from '../types';

function getPeriodDateRange(year: number, month: number) {
  const pad = (n: number) => String(n).padStart(2, '0');
  const lastDay = new Date(year, month, 0).getDate();
  return {
    startDate: `${year}-${pad(month)}-01T00:00:00`,
    endDate: `${year}-${pad(month)}-${pad(lastDay)}T23:59:59.999999`,
  };
}

export function useFinance(userId: number) {
  const [summary, setSummary] = useState<FinancialSummary | null>(null);
  const [periodAnalytics, setPeriodAnalytics] = useState<PeriodAnalytics | null>(null);
  const [transactionsPage, setTransactionsPage] = useState<PageResponse<Transaction> | null>(null);
  const [loadingSummary, setLoadingSummary] = useState<boolean>(true);
  const [loadingTransactions, setLoadingTransactions] = useState<boolean>(true);
  const [isSyncing, setIsSyncing] = useState<boolean>(false);
  const [syncResult, setSyncResult] = useState<EmailSyncResponse | null>(null);
  const [syncStatus, setSyncStatus] = useState<GoogleAuthStatus | null>(null);
  const [syncStatusUnavailable, setSyncStatusUnavailable] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [selectedPeriod, setSelectedPeriodState] = useState<{ year: number; month: number }>({
    year: new Date().getFullYear(),
    month: new Date().getMonth() + 1,
  });

  const [filters, setFilters] = useState<TransactionFilterParams>({
    page: 0,
    size: 10,
    flowType: '',
    search: '',
  });

  const setSelectedPeriod = (period: { year: number; month: number }) => {
    setSelectedPeriodState(period);
    setFilters((prev) => ({ ...prev, startDate: undefined, endDate: undefined, page: 0 }));
  };

  const transactionFilters = useMemo(() => ({
    ...filters,
    ...(filters.startDate && filters.endDate ? {} : getPeriodDateRange(selectedPeriod.year, selectedPeriod.month)),
  }), [filters, selectedPeriod]);

  const loadSummary = useCallback(async (silent = false) => {
    try {
      if (!silent) setLoadingSummary(true);
      const [sumData, periodData] = await Promise.all([
        api.getFinancialSummary(),
        api.getPeriodAnalytics(selectedPeriod.year, selectedPeriod.month),
      ]);
      setSummary(sumData);
      setPeriodAnalytics(periodData);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al cargar analítica financiera');
    } finally {
      if (!silent) setLoadingSummary(false);
    }
  }, [selectedPeriod]);

  const loadTransactions = useCallback(async (silent = false) => {
    try {
      if (!silent) setLoadingTransactions(true);
      const data = await api.getTransactions(transactionFilters);
      setTransactionsPage(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al cargar transacciones');
    } finally {
      if (!silent) setLoadingTransactions(false);
    }
  }, [transactionFilters]);

  const loadSyncStatus = useCallback(async () => {
    if (!userId) {
      setSyncStatus(null);
      setSyncStatusUnavailable(false);
      return;
    }
    try {
      const status = await api.getGoogleAuthStatus();
      setSyncStatus(status);
      setSyncStatusUnavailable(false);
    } catch {
      setSyncStatusUnavailable(true);
    }
  }, [userId]);

  useEffect(() => {
    loadSummary();
  }, [loadSummary]);

  useEffect(() => {
    loadTransactions();
  }, [loadTransactions]);

  useEffect(() => {
    setSyncStatus(null);
    loadSyncStatus();
  }, [loadSyncStatus]);

  useEffect(() => {
    const interval = setInterval(() => {
      if (!isSyncing) {
        // Silent background polling: keeps data up to date without screen flickering
        loadSummary(true);
        loadTransactions(true);
        if (userId) loadSyncStatus();
      }
    }, 45000);

    return () => clearInterval(interval);
  }, [loadSummary, loadTransactions, loadSyncStatus, isSyncing, userId]);

  const triggerEmailSync = async () => {
    try {
      setIsSyncing(true);
      setError(null);
      const result = await api.syncEmails();
      setSyncResult(result);
      await Promise.all([loadSummary(), loadTransactions()]);
      return result;
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Error al sincronizar correos';
      setError(msg);
      throw err;
    } finally {
      await loadSyncStatus();
      setIsSyncing(false);
    }
  };

  const handlePageChange = (newPage: number) => {
    setFilters((prev) => ({ ...prev, page: newPage }));
  };

  const handleFilterChange = (newFilters: Partial<TransactionFilterParams>) => {
    setFilters((prev) => ({ ...prev, ...newFilters, page: 0 }));
  };

  return {
    summary,
    periodAnalytics,
    transactionsPage,
    loadingSummary,
    loadingTransactions,
    isSyncing,
    syncResult,
    syncStatus,
    syncStatusUnavailable,
    error,
    filters,
    transactionFilters,
    selectedPeriod,
    setSelectedPeriod,
    handlePageChange,
    handleFilterChange,
    triggerEmailSync,
    refreshSyncStatus: loadSyncStatus,
    refreshAll: async () => {
      await Promise.all([loadSummary(), loadTransactions()]);
    },
  };
}
