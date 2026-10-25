// @vitest-environment jsdom
import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { useFinance } from '../src/hooks/useFinance';
import { api } from '../src/services/api';
import type { PageResponse, PeriodAnalytics, Transaction, TransactionPageResponse } from '../src/types';

const transaction: Transaction = { id: 1, amount: 10, flowType: 'EXPENSE', contactName: 'Synthetic merchant', channel: 'YAPE', transactionDate: '2026-10-01T12:00:00', createdAt: '2026-10-01T12:00:00', transactionHash: 'synthetic' };
const page: PageResponse<Transaction> = { content: [transaction], totalElements: 1, totalPages: 1, number: 0, size: 10, first: true, last: true, empty: false };
const analytics: PeriodAnalytics = { categoryBreakdown: [], periodName: '10/2026', monthlyExpense: 10, monthlyIncome: 20, internalTransfersAmount: 0, previousPeriodExpense: null, comparisonThroughDay: null, dailyExpenses: [], totalMovements: 1, lastExpenseMerchant: 'Synthetic merchant', lastExpenseAmount: 10, lastExpenseDate: null, topChannel: 'YAPE', topChannelAmount: 10, topChannelPercentage: 100, channelBreakdown: [], topMerchants: [] };
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: Error) => void;
  const promise = new Promise<T>((accept, fail) => { resolve = accept; reject = fail; });
  return { promise, resolve, reject };
}

beforeEach(() => {
  vi.spyOn(api, 'getFinancialSummary').mockResolvedValue({ totalIncome: 20, totalExpense: 10, netBalance: 10, totalTransactions: 1 });
  vi.spyOn(api, 'getPeriodAnalytics').mockResolvedValue(analytics);
  vi.spyOn(api, 'getGoogleAuthStatus').mockResolvedValue({ connected: false, email: null, lastSuccessfulSyncAt: null, lastSyncFailed: false });
  vi.spyOn(api, 'getTransactions').mockResolvedValue(page);
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); });

it('keeps the latest search when an old request finishes later', async () => {
  const old = deferred<TransactionPageResponse>();
  vi.mocked(api.getTransactions).mockImplementation(params => params?.size === 5 ? Promise.resolve(page) : params?.search === 'new' ? Promise.resolve({ ...page,
    summary: { totalExpense: 123.45, totalIncome: 0, internalTransfersAmount: 0 },
    content: [{ ...transaction, contactName: 'New query' }] }) : old.promise);
  const { result } = renderHook(() => useFinance(1));
  act(() => result.current.handleFilterChange({ search: 'new' }));
  await waitFor(() => expect(result.current.transactionsPage?.content[0].contactName).toBe('New query'));
  await act(async () => old.resolve({ ...page, summary: { totalExpense: 999, totalIncome: 0, internalTransfersAmount: 0 } }));
  expect(result.current.transactionsPage?.content[0].contactName).toBe('New query');
  expect(result.current.transactionsPage?.summary?.totalExpense).toBe(123.45);
  expect(result.current.loadingTransactions).toBe(false);
});

it('does not show old month data or stop loading when its response arrives', async () => {
  const old = deferred<PeriodAnalytics>();
  const current = deferred<PeriodAnalytics>();
  vi.mocked(api.getPeriodAnalytics).mockImplementation((_year, month) => month === 2 ? current.promise : old.promise);
  const { result } = renderHook(() => useFinance(1));
  act(() => result.current.setSelectedPeriod({ year: 2024, month: 2 }));
  await act(async () => old.resolve(analytics));
  expect(result.current.periodAnalytics).toBeNull();
  expect(result.current.loadingSummary).toBe(true);
  await act(async () => current.resolve({ ...analytics, monthlyExpense: 99 }));
  expect(result.current.periodAnalytics?.monthlyExpense).toBe(99);
  expect(result.current.loadingSummary).toBe(false);
});

it('ignores an error from a superseded request', async () => {
  const old = deferred<PageResponse<Transaction>>();
  vi.mocked(api.getTransactions).mockImplementation(params => params?.size === 5 || params?.search === 'new' ? Promise.resolve(page) : old.promise);
  const { result } = renderHook(() => useFinance(1));
  act(() => result.current.handleFilterChange({ search: 'new' }));
  await waitFor(() => expect(result.current.loadingTransactions).toBe(false));
  await act(async () => old.reject(new Error('Stale failure')));
  expect(result.current.error).toBeNull();
});

it('clears a recovered resource error without clearing another resource failure', async () => {
  vi.mocked(api.getTransactions).mockImplementation(params => params?.size === 5 ? Promise.resolve(page) : Promise.reject(new Error('Transaction outage')));
  vi.mocked(api.getPeriodAnalytics).mockRejectedValue(new Error('Analytics outage'));
  const { result } = renderHook(() => useFinance(1));
  await waitFor(() => expect(result.current.error).toContain('Transaction outage'));
  vi.mocked(api.getTransactions).mockResolvedValue(page);
  await act(async () => result.current.refreshAll());
  expect(result.current.error).toBe('Analytics outage');
  vi.mocked(api.getPeriodAnalytics).mockResolvedValue(analytics);
  await act(async () => result.current.refreshAll());
  expect(result.current.error).toBeNull();
});

it('keeps recent movements independent of search, custom dates and pagination', async () => {
  vi.mocked(api.getTransactions).mockImplementation(params => Promise.resolve(params?.size === 5 ? page : { ...page, content: [] }));
  const { result } = renderHook(() => useFinance(1));
  await waitFor(() => expect(result.current.recentTransactions).toHaveLength(1));
  act(() => result.current.handleFilterChange({ search: 'No matches', startDate: '2024-01-01T00:00:00', endDate: '2024-01-02T23:59:59' }));
  act(() => result.current.handlePageChange(3));
  await waitFor(() => expect(result.current.loadingTransactions).toBe(false));
  expect(result.current.transactionsPage?.content).toEqual([]);
  expect(result.current.recentTransactions).toEqual([transaction]);
  const recentCalls = vi.mocked(api.getTransactions).mock.calls.filter(([params]) => params?.size === 5);
  expect(recentCalls).toHaveLength(1);
  expect(recentCalls[0][0]).toEqual(expect.objectContaining({ page: 0, size: 5 }));
  expect(recentCalls[0][0]?.search).toBeUndefined();
});

it('does not publish old user results after the user changes', async () => {
  const old = deferred<PageResponse<Transaction>>();
  vi.mocked(api.getTransactions).mockReturnValue(old.promise);
  const { result, rerender } = renderHook(({ user }) => useFinance(user), { initialProps: { user: 1 } });
  vi.mocked(api.getTransactions).mockResolvedValue({ ...page, content: [] });
  rerender({ user: 2 });
  await waitFor(() => expect(result.current.loadingTransactions).toBe(false));
  await act(async () => old.resolve(page));
  expect(result.current.transactionsPage?.content).toEqual([]);
  expect(result.current.recentTransactions).toEqual([]);
});

it('prevents financial reads without an authenticated user', async () => {
  const { result } = renderHook(() => useFinance(0));
  await waitFor(() => expect(result.current.loadingSummary).toBe(false));
  expect(api.getTransactions).not.toHaveBeenCalled();
  expect(api.getPeriodAnalytics).not.toHaveBeenCalled();
  expect(api.getFinancialSummary).not.toHaveBeenCalled();
});
