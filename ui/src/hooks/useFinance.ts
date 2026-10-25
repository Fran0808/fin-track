import { useState, useEffect, useCallback, useMemo } from 'react';
import { api } from '../services/api';
import type { TransactionFilterParams } from '../services/api';
import type { FinancialSummary, PeriodAnalytics, Transaction, PageResponse, EmailSyncResponse, GoogleAuthStatus } from '../types';
import { useQueryResource } from './useQueryResource';

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
  const [syncResult, setSyncResult] = useState<EmailSyncResponse | null>(null);
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
  const summaryResource = useQueryResource<FinancialSummary>(summaryQuery, 'Error al cargar el resumen');
  const analyticsResource = useQueryResource<PeriodAnalytics>(analyticsQuery, 'Error al cargar analítica financiera');
  const transactionResource = useQueryResource<PageResponse<Transaction>>(transactionsQuery, 'Error al cargar transacciones');
  useEffect(() => {
    const page = transactionResource.data;
    if (page && page.number > 0 && page.number >= page.totalPages) {
      setFilters(current => ({ ...current, page: Math.max(0, page.totalPages - 1) }));
    }
  }, [transactionResource.data]);
  const recentResource = useQueryResource<PageResponse<Transaction>>(recentQuery, 'Error al cargar movimientos recientes');
  const { refresh: loadSummary } = summaryResource;
  const { refresh: loadAnalytics } = analyticsResource;
  const { refresh: loadTransactions } = transactionResource;
  const { refresh: loadRecent } = recentResource;
  const error = [syncError, summaryResource.error, analyticsResource.error, transactionResource.error, recentResource.error]
    .filter((message, index, messages) => message && messages.indexOf(message) === index).join(' · ') || null;

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
    loadSyncStatus();
  }, [loadSyncStatus]);

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

  const triggerEmailSync = async () => {
    try {
      setIsSyncing(true);
      setSyncError(null);
      const result = await api.syncEmails();
      setSyncResult(result);
      await Promise.all([loadSummary(), loadAnalytics(), loadTransactions(), loadRecent()]);
      return result;
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Error al sincronizar correos';
      setSyncError(msg);
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

  const showMerchantTransactions = (merchantName: string, startDate: string, endDate: string) => {
    setFilters({ page: 0, size: 10, flowType: 'EXPENSE', search: '', merchantName,
      startDate, endDate, allHistory: false });
  };

  const showCategoryTransactions = (category: string | null, startDate: string, endDate: string) => {
    setFilters({ page: 0, size: 10, flowType: 'EXPENSE', search: '', categoryGroup: category ?? '',
      startDate, endDate, allHistory: false });
  };

  return {
    summary: summaryResource.data,
    periodAnalytics: analyticsResource.data,
    transactionsPage: transactionResource.data,
    recentTransactions: recentResource.data?.content ?? [],
    loadingRecent: recentResource.loading,
    loadingSummary: analyticsResource.loading,
    loadingTransactions: transactionResource.loading,
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
    showMerchantTransactions,
    showCategoryTransactions,
    triggerEmailSync,
    refreshSyncStatus: loadSyncStatus,
    refreshAll: async () => {
      await Promise.all([loadSummary(), loadAnalytics(), loadTransactions(), loadRecent()]);
    },
  };
}
