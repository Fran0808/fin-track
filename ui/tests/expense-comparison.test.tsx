// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import { ExpenseComparison } from '../src/components/analytics/ExpenseComparison';
import type { PeriodAnalytics } from '../src/types';
const analytics = { monthlyExpense: 100, previousPeriodExpense: 70, expenseDifference: 30,
  periodStart: '2026-10-01T00:00:00', periodEnd: '2026-10-24T23:59:59.999999999',
  previousPeriodStart: '2026-09-01T00:00:00', previousPeriodEnd: '2026-09-24T23:59:59.999999999' } as PeriodAnalytics;
afterEach(cleanup);
it('shows both exact ranges, amounts, proportional bars and the difference in soles', () => {
  render(<ExpenseComparison analytics={analytics} loading={false} />);
  expect(screen.getByText('01/10/2026 al 24/10/2026')).toBeDefined();
  expect(screen.getByText('01/09/2026 al 24/09/2026')).toBeDefined();
  expect(screen.getByText('S/ 100.00')).toBeDefined();expect(screen.getByText('S/ 70.00')).toBeDefined();
  expect(screen.getByText('S/ 30.00 más')).toBeDefined();
  expect(screen.getByText('+42.9% vs. mes ant.')).toBeDefined();
  expect(screen.getByTestId('current-period-bar').style.width).toBe('100%');
  expect(screen.getByTestId('previous-period-bar').style.width).toBe('70%');
});
it('handles zero previous spending without division and expresses decreases and equal amounts', () => {
  const view = render(<ExpenseComparison analytics={{ ...analytics, previousPeriodExpense: 0, expenseDifference: 100 }} loading={false} />);
  expect(screen.getByText('S/ 100.00 más')).toBeDefined();
  expect(screen.queryByText(/% vs\. mes ant\./)).toBeNull();
  view.rerender(<ExpenseComparison analytics={{ ...analytics, expenseDifference: -20 }} loading={false} />);
  expect(screen.getByText('S/ 20.00 menos')).toBeDefined();
  expect(screen.getByText('-28.6% vs. mes ant.')).toBeDefined();
  view.rerender(<ExpenseComparison analytics={{ ...analytics, expenseDifference: 0 }} loading={false} />);
  expect(screen.getByText('Sin diferencia')).toBeDefined();
});
it('does not present missing comparisons as zero and hides values while loading', () => {
  const view = render(<ExpenseComparison analytics={analytics} loading />);
  expect(screen.getByRole('status').textContent).toBe('Calculando comparación...');expect(screen.queryByText('S/ 100.00')).toBeNull();
  view.rerender(<ExpenseComparison analytics={{ ...analytics, expenseDifference: null }} loading={false} />);
  expect(screen.getByText('Comparación no disponible para este período.')).toBeDefined();
});
