// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import { ExpenseHighlights } from '../src/components/analytics/ExpenseHighlights';
import type { PeriodAnalytics } from '../src/types';
const analytics = { largestExpense: { merchantName: 'Synthetic shop', amount: 80, transactionDate: '2026-01-02T12:00:00' },
  peakExpenseDay: { date: '2026-01-01', amount: 100 } } as PeriodAnalytics;
afterEach(cleanup);
it('distinguishes the largest individual movement from the highest daily sum', () => {
  render(<ExpenseHighlights analytics={analytics} loading={false} />);
  expect(screen.getByText('Mayor gasto registrado')).toBeDefined();expect(screen.getByText('S/ 80.00')).toBeDefined();
  expect(screen.getByText('Synthetic shop')).toBeDefined();expect(screen.getByText('S/ 100.00')).toBeDefined();
  expect(screen.getByText('01/01/2026')).toBeDefined();expect(screen.queryByRole('button')).toBeNull();
});
it('handles missing data, empty periods and loading without inventing a maximum of zero', () => {
  const view = render(<ExpenseHighlights analytics={analytics} loading />);
  expect(screen.getByRole('status').textContent).toBe('Calculando datos destacados...');expect(screen.queryByText('S/ 80.00')).toBeNull();
  view.rerender(<ExpenseHighlights analytics={{ ...analytics, largestExpense: null, peakExpenseDay: null }} loading={false} />);
  expect(screen.getByText('No hay gastos registrados para destacar en este período.')).toBeDefined();
  view.rerender(<ExpenseHighlights analytics={null} loading={false} />);
  expect(screen.getByText('Datos destacados no disponibles para este período.')).toBeDefined();
});
