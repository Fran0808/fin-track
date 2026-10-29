// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { ExpenseTrendChart } from '../src/components/charts/ExpenseTrendChart';
const points = [{ day: 1, amount: 12, cumulativeAmount: 12 }, { day: 2, amount: 0, cumulativeAmount: 12 }, { day: 3, amount: 5, cumulativeAmount: 17 }];
afterEach(cleanup);
it('defaults to daily bars including zero days and switches to the cumulative line', () => {
  const view = render(<ExpenseTrendChart points={points} monthLabel="enero de 2026" />);
  expect(screen.getByRole('button', { name: 'Diario' }).getAttribute('aria-pressed')).toBe('true');
  expect(view.container.querySelectorAll('rect').length).toBe(3);
  expect(view.container.querySelectorAll('rect')[1].getAttribute('height')).toBe('0');
  fireEvent.click(screen.getByRole('button', { name: 'Acumulado' }));
  expect(view.container.querySelectorAll('rect').length).toBe(0);
  expect(screen.getByRole('group', { name: /Gasto acumulado en/ })).toBeDefined();
  expect(screen.getByText('S/ 17.00', { selector: 'p' })).toBeDefined();
});
it('allows keyboard exploration in both views without replacing daily values with cumulative totals', () => {
  render(<ExpenseTrendChart points={points} monthLabel="enero de 2026" />);
  const chart = screen.getByRole('group', { name: /Gasto diario en/ });
  fireEvent.focus(chart);fireEvent.keyDown(chart, { key: 'ArrowLeft' });
  expect(within(screen.getByRole('status')).getByText('S/ 0.00')).toBeDefined();
  expect(within(screen.getByRole('status')).getByText('S/ 12.00')).toBeDefined();
  fireEvent.click(screen.getByRole('button', { name: 'Acumulado' }));
  fireEvent.keyDown(chart, { key: 'ArrowRight' });
  expect(within(screen.getByRole('status')).getByText('S/ 5.00')).toBeDefined();
  expect(within(screen.getByRole('status')).getByText('S/ 17.00')).toBeDefined();
});
it('triggers onSelectDay when clicking a bar with expenses or pressing Enter, and ignores zero-expense days', () => {
  const onSelectDay = vi.fn();
  const view = render(<ExpenseTrendChart points={points} monthLabel="enero de 2026" onSelectDay={onSelectDay} />);
  const bars = view.container.querySelectorAll('rect');
  // Clicking day 2 (0 amount) does not trigger onSelectDay
  fireEvent.click(bars[1]);
  expect(onSelectDay).not.toHaveBeenCalled();

  // Clicking day 1 (amount 12) triggers onSelectDay(1)
  fireEvent.click(bars[0]);
  expect(onSelectDay).toHaveBeenCalledWith(1);

  // Focusing the chart selects the last point (day 3, amount 5), shows hint, and Enter triggers onSelectDay(3)
  const chart = screen.getByRole('group', { name: /Gasto diario en/ });
  fireEvent.focus(chart);
  expect(within(screen.getByRole('status')).getByText('Clic para ver movimientos →')).toBeDefined();
  fireEvent.keyDown(chart, { key: 'Enter' });
  expect(onSelectDay).toHaveBeenLastCalledWith(3);
});
it('handles a single day and empty or zero-only periods', () => {
  const view = render(<ExpenseTrendChart points={[points[0]]} monthLabel="enero de 2026" />);
  const bar = view.container.querySelector('rect')!;
  expect(Number(bar.getAttribute('x'))).toBeGreaterThan(0);
  view.rerender(<ExpenseTrendChart points={[]} monthLabel="enero de 2026" />);
  expect(screen.getByText('Aún no hay gastos para mostrar en este período.')).toBeDefined();
  view.rerender(<ExpenseTrendChart points={[{ day: 1, amount: 0, cumulativeAmount: 0 }]} monthLabel="enero de 2026" />);
  expect(screen.queryByRole('group', { name: /Gasto diario en/ })).toBeNull();
});
