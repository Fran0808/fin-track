// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, renderHook, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { TransactionFilters } from '../src/components/transactions/TransactionFilters';
import { TransactionDateFilter } from '../src/components/transactions/TransactionDateFilter';
import { SidebarNavigation } from '../src/components/layout/SidebarNavigation';
import { useDashboardNavigation } from '../src/hooks/useDashboardNavigation';
import { useFinance } from '../src/hooks/useFinance';
import { api } from '../src/services/api';

beforeEach(() => {
  window.history.replaceState({}, '', '/');
  vi.spyOn(api, 'getCategories').mockResolvedValue([]);
  vi.spyOn(api, 'getFinancialSummary').mockRejectedValue(new Error('synthetic unavailable'));
  vi.spyOn(api, 'getPeriodAnalytics').mockRejectedValue(new Error('synthetic unavailable'));
  vi.spyOn(api, 'getGoogleAuthStatus').mockResolvedValue({ connected: false });
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); window.history.replaceState({}, '', '/'); });

it('replaces Pendientes navigation with Movimientos and preserves legacy review scope', async () => {
  window.history.replaceState({}, '', '/?vista=pendientes');
  const list = vi.spyOn(api, 'getTransactions').mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, size: 10, number: 0, first: true, last: true, empty: true });
  const nav = renderHook(() => useDashboardNavigation());
  const finance = renderHook(() => useFinance(1));
  expect(nav.result.current[0]).toBe('movimientos');
  render(<SidebarNavigation activeView={nav.result.current[0]} onViewChange={vi.fn()} />);
  expect(screen.queryByRole('button', { name: 'Pendientes' })).toBeNull();
  expect(screen.getByRole('button', { name: 'Reglas' })).toBeDefined();
  await waitFor(() => expect(list).toHaveBeenCalledWith(expect.objectContaining({ pendingReason: 'ALL', startDate: undefined, endDate: undefined })));
  act(() => finance.result.current.handleFilterChange({ search: 'CINE', pendingReason: 'CATEGORY', allHistory: true }));
  act(() => finance.result.current.handlePageChange(2));
  expect(finance.result.current.transactionFilters).toEqual(expect.objectContaining({ page: 2, search: 'CINE', pendingReason: 'CATEGORY', startDate: undefined, endDate: undefined }));
  act(() => finance.result.current.setSelectedPeriod({ year: 2026, month: 9 }));
  expect(finance.result.current.transactionFilters).toEqual(expect.objectContaining({ page: 0, pendingReason: 'CATEGORY', allHistory: false, startDate: '2026-09-01T00:00:00' }));
});

it('selects missing category across history, clearing contradictory category and dates', async () => {
  const change = vi.fn();
  const view = render(<TransactionFilters search="CINE" flowType="EXPENSE" category="Ocio" onFilterChange={change} onReset={vi.fn()} />);
  fireEvent.click(screen.getByRole('button', { name: /Filtros avanzados/ }));
  fireEvent.change(screen.getByLabelText('Revisión'), { target: { value: 'CATEGORY' } });
  expect(change).toHaveBeenCalledWith({ pendingReason: 'CATEGORY', allHistory: true, startDate: undefined, endDate: undefined, category: '' });
  view.rerender(<TransactionFilters search="CINE" flowType="EXPENSE" pendingReason="CATEGORY" allHistory onFilterChange={change} onReset={vi.fn()} />);
  expect((screen.getByLabelText('Categoría') as HTMLSelectElement).disabled).toBe(true);
  expect(screen.getByText('Se muestra todo el historial, sin límite de fechas.')).toBeDefined();
  fireEvent.change(screen.getByLabelText('Revisión'), { target: { value: 'INSTRUMENT' } });
  expect(change).toHaveBeenLastCalledWith({ pendingReason: 'INSTRUMENT', allHistory: true, startDate: undefined, endDate: undefined });
  await waitFor(() => expect(api.getCategories).toHaveBeenCalled());
});

it('allows restricting all-history review back to a month or date', () => {
  const change = vi.fn();
  render(<TransactionDateFilter allHistory onFilterChange={change} />);
  fireEvent.click(screen.getByRole('button', { name: 'Mes seleccionado' }));
  expect(change).toHaveBeenLastCalledWith({ allHistory: false, startDate: undefined, endDate: undefined });
  fireEvent.click(screen.getByRole('button', { name: 'Todo el historial' }));
  expect(change).toHaveBeenLastCalledWith({ allHistory: true, startDate: undefined, endDate: undefined });
});

it('returns to a valid page after completing the final movement on the last review page', async () => {
  const list = vi.spyOn(api, 'getTransactions').mockImplementation(async params => ({ content: [], totalElements: 10,
    totalPages: 1, size: params?.size ?? 10, number: params?.page ?? 0, first: !params?.page, last: true, empty: true }));
  const { result } = renderHook(() => useFinance(1));
  act(() => result.current.handleFilterChange({ pendingReason: 'INSTRUMENT', allHistory: true }));
  await waitFor(() => expect(list).toHaveBeenCalledWith(expect.objectContaining({ pendingReason: 'INSTRUMENT', page: 0 })));
  act(() => result.current.handlePageChange(1));
  await waitFor(() => expect(result.current.filters.page).toBe(0));
  expect(result.current.transactionFilters).toEqual(expect.objectContaining({ pendingReason: 'INSTRUMENT', allHistory: true }));
});

it('sends identical review filters for listing and CSV and refuses servers that ignore them', async () => {
  const fetch = vi.fn().mockImplementation(async () => new Response('{}', { headers: { 'X-Fintrack-Review-Filter': 'CATEGORY' } }));
  vi.stubGlobal('fetch', fetch);
  const filters = { pendingReason: 'CATEGORY' as const, allHistory: true, search: 'CINE', flowType: 'EXPENSE' as const, minAmount: 0 };
  await api.getTransactions(filters);
  await api.exportTransactionsCsv(filters);
  const list = new URL(fetch.mock.calls[0][0], 'http://localhost');
  const csv = new URL(fetch.mock.calls[1][0], 'http://localhost');
  list.searchParams.delete('page'); list.searchParams.delete('size');
  expect([...list.searchParams.entries()].sort()).toEqual([...csv.searchParams.entries()].sort());
  expect(csv.searchParams.get('pendingReason')).toBe('CATEGORY');
  expect(csv.searchParams.has('allHistory')).toBe(false);
  expect(csv.searchParams.has('startDate')).toBe(false);
  fetch.mockImplementation(async () => new Response('{}'));
  await expect(api.getTransactions(filters)).rejects.toThrow('servidor necesita actualizarse');
  await expect(api.exportTransactionsCsv(filters)).rejects.toThrow('servidor necesita actualizarse');
});
