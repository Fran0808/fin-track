// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, renderHook, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { TransactionDateFilter } from '../src/components/transactions/TransactionDateFilter';
import { useFinance } from '../src/hooks/useFinance';
import { api } from '../src/services/api';

beforeEach(() => {
  vi.spyOn(api, 'getFinancialSummary').mockRejectedValue(new Error('Summary unavailable in this test'));
  vi.spyOn(api, 'getPeriodAnalytics').mockRejectedValue(new Error('Analytics unavailable in this test'));
  vi.spyOn(api, 'getGoogleAuthStatus').mockResolvedValue({ connected: false });
  vi.spyOn(api, 'getTransactions').mockResolvedValue({ content: [], totalElements: 0,
    totalPages: 0, size: 10, number: 0, first: true, last: true, empty: true });
});

afterEach(() => { cleanup(); vi.restoreAllMocks(); });

it('searches a specific day including its final fractional second', () => {
  const onFilterChange = vi.fn();
  render(<TransactionDateFilter onFilterChange={onFilterChange} />);
  fireEvent.click(screen.getByRole('button', { name: 'Día específico' }));
  const apply = screen.getByRole('button', { name: 'Buscar por fecha' }) as HTMLButtonElement;
  expect(apply.disabled).toBe(true);
  fireEvent.change(screen.getByLabelText('Fecha'), { target: { value: '2026-10-06' } });
  fireEvent.click(apply);
  expect(onFilterChange).toHaveBeenCalledWith({
    startDate: '2026-10-06T00:00:00', endDate: '2026-10-06T23:59:59.999999',
  });
});

it('requires both dates, rejects reversed ranges and supports ranges across months', () => {
  const onFilterChange = vi.fn();
  render(<TransactionDateFilter onFilterChange={onFilterChange} />);
  fireEvent.click(screen.getByRole('button', { name: 'Rango de fechas' }));
  const apply = screen.getByRole('button', { name: 'Buscar por fecha' }) as HTMLButtonElement;
  fireEvent.change(screen.getByLabelText('Desde'), { target: { value: '2026-09-30' } });
  expect(apply.disabled).toBe(true);
  fireEvent.change(screen.getByLabelText('Hasta'), { target: { value: '2026-09-01' } });
  expect(screen.getByRole('alert').textContent).toContain('posterior');
  expect(apply.disabled).toBe(true);
  fireEvent.click(apply);
  expect(onFilterChange).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText('Hasta'), { target: { value: '2026-10-08' } });
  fireEvent.click(apply);
  expect(onFilterChange).toHaveBeenCalledWith({
    startDate: '2026-09-30T00:00:00', endDate: '2026-10-08T23:59:59.999999',
  });
});

it('returns to the selected month without retaining the custom dates', () => {
  const onFilterChange = vi.fn();
  render(<TransactionDateFilter startDate="2026-09-30T00:00:00" endDate="2026-10-08T23:59:59.999999" onFilterChange={onFilterChange} />);
  expect(screen.getByText(/Búsqueda aplicada/).textContent).toContain('30/09/2026 al 08/10/2026');
  fireEvent.click(screen.getByRole('button', { name: 'Mes seleccionado' }));
  expect(onFilterChange).toHaveBeenCalledWith({ startDate: undefined, endDate: undefined });
});

it('sends custom dates to the API, keeps them across pagination, and restores the month on period change', async () => {
  const { result } = renderHook(() => useFinance(1));
  await waitFor(() => expect(api.getTransactions).toHaveBeenCalled());
  const customDates = { startDate: '2026-09-30T00:00:00', endDate: '2026-10-08T23:59:59.999999' };
  act(() => result.current.handlePageChange(3));
  act(() => result.current.handleFilterChange(customDates));
  await waitFor(() => expect(api.getTransactions).toHaveBeenLastCalledWith(expect.objectContaining({ ...customDates, page: 0 })));
  expect(result.current.transactionFilters).toEqual(expect.objectContaining(customDates));
  act(() => result.current.handlePageChange(1));
  await waitFor(() => expect(api.getTransactions).toHaveBeenLastCalledWith(expect.objectContaining({ ...customDates, page: 1 })));
  act(() => result.current.setSelectedPeriod({ year: 2024, month: 2 }));
  await waitFor(() => expect(api.getTransactions).toHaveBeenLastCalledWith(expect.objectContaining({
    startDate: '2024-02-01T00:00:00', endDate: '2024-02-29T23:59:59.999999', page: 0,
  })));
  expect(result.current.filters.startDate).toBeUndefined();
});
