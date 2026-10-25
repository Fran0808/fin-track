// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
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
it('handles a single day and empty or zero-only periods', () => {
  const view = render(<ExpenseTrendChart points={[points[0]]} monthLabel="enero de 2026" />);
  const bar = view.container.querySelector('rect')!;
  expect(Number(bar.getAttribute('x'))).toBeGreaterThan(0);
  view.rerender(<ExpenseTrendChart points={[]} monthLabel="enero de 2026" />);
  expect(screen.getByText('Aún no hay gastos para mostrar en este período.')).toBeDefined();
  view.rerender(<ExpenseTrendChart points={[{ day: 1, amount: 0, cumulativeAmount: 0 }]} monthLabel="enero de 2026" />);
  expect(screen.queryByRole('group', { name: /Gasto diario en/ })).toBeNull();
});
