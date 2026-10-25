// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { MyMoneyView } from '../src/components/views/MyMoneyView';
import type { PeriodAnalytics } from '../src/types';

const analytics: PeriodAnalytics = { periodName: 'JANUARY', monthlyExpense: 100, monthlyIncome: 200,
  internalTransfersAmount: 50, previousPeriodExpense: null, comparisonThroughDay: null, dailyExpenses: [],
  totalMovements: 5, lastExpenseMerchant: '', lastExpenseAmount: 0, lastExpenseDate: null, topChannel: '',
  topChannelAmount: 0, topChannelPercentage: 0, channelBreakdown: [], topMerchants: [],
  categoryBreakdown: [{ category: 'Comida', amount: 80, percentage: 80, count: 2 }, { category: null, amount: 20, percentage: 20, count: 2 }] };
afterEach(cleanup);

it('opens the selected merchant by its exact name without changing the supplied data', () => {
  const select = vi.fn();
  render(<MyMoneyView analytics={{ ...analytics, topMerchants: [{ merchantName: 'Exact % Shop', totalAmount: 100, percentage: 100, transactionCount: 2 }] }}
    loading={false} selectedYear={2026} selectedMonth={1} onSelectMerchant={select} />);
  fireEvent.click(screen.getByRole('button', { name: 'Ver movimientos de Exact % Shop' }));
  expect(select).toHaveBeenCalledWith('Exact % Shop');
});

it('shows category amounts, shares and uncategorized expenses', () => {
  render(<MyMoneyView analytics={analytics} loading={false} selectedYear={2026} selectedMonth={1} />);
  const section = screen.getByRole('region', { name: 'Gastos por categoría' });
  expect(within(section).getByText('Comida')).toBeDefined();
  expect(within(section).getByText('Sin categoría')).toBeDefined();
  expect(within(section).getByText('S/ 80.00')).toBeDefined();
  expect(within(section).getByText('20.0% del gasto')).toBeDefined();
});

it('opens category groups using their actual value, including null for uncategorized', () => {
  const select = vi.fn();
  render(<MyMoneyView analytics={analytics} loading={false} selectedYear={2026} selectedMonth={1} onSelectCategory={select} />);
  fireEvent.click(screen.getByRole('button', { name: 'Ver movimientos de la categoría Comida' }));
  expect(select).toHaveBeenLastCalledWith('Comida');
  fireEvent.click(screen.getByRole('button', { name: 'Ver movimientos de la categoría Sin categoría' }));
  expect(select).toHaveBeenLastCalledWith(null);
});

it('distinguishes loading, empty data and an older API', () => {
  const view = render(<MyMoneyView analytics={null} loading selectedYear={2026} selectedMonth={1} />);
  expect(screen.getByText('Calculando categorías...')).toBeDefined();
  view.rerender(<MyMoneyView analytics={{ ...analytics, categoryBreakdown: [] }} loading={false} selectedYear={2026} selectedMonth={1} />);
  expect(within(screen.getByRole('region', { name: 'Gastos por categoría' })).getByText('No hay gastos registrados en este período.')).toBeDefined();
  const legacy = { ...analytics };
  Reflect.deleteProperty(legacy, 'categoryBreakdown');
  view.rerender(<MyMoneyView analytics={legacy} loading={false} selectedYear={2026} selectedMonth={1} />);
  expect(screen.getByRole('alert').textContent).toContain('API actualizada');
});
