// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { ExpenseTrendChart } from '../src/components/charts/ExpenseTrendChart';

const points = [
  { day: 1, amount: 12, cumulativeAmount: 12 },
  { day: 2, amount: 0, cumulativeAmount: 12 },
  { day: 3, amount: 5, cumulativeAmount: 17 },
];

afterEach(cleanup);

it('defaults to daily bars including zero days and switches to weekly bars', () => {
  const view = render(<ExpenseTrendChart points={points} monthLabel="enero de 2026" />);
  expect(screen.getByRole('button', { name: 'Diario' }).getAttribute('aria-pressed')).toBe('true');
  expect(view.container.querySelectorAll('rect').length).toBe(3);
  expect(view.container.querySelectorAll('rect')[1].getAttribute('height')).toBe('0');

  fireEvent.click(screen.getByRole('button', { name: 'Semanal' }));
  expect(screen.getByRole('button', { name: 'Semanal' }).getAttribute('aria-pressed')).toBe('true');
  expect(screen.getByRole('group', { name: /Gasto semanal en/ })).toBeDefined();
  expect(screen.getByText('Sem 1')).toBeDefined();
});

it('allows keyboard exploration in both views and shows weekly breakdown', () => {
  render(<ExpenseTrendChart points={points} monthLabel="enero de 2026" />);
  const chart = screen.getByRole('group', { name: /Gasto diario en/ });
  fireEvent.focus(chart);
  fireEvent.keyDown(chart, { key: 'ArrowLeft' });
  expect(within(screen.getByRole('status')).getByText(/0.00/)).toBeDefined();
  fireEvent.keyDown(chart, { key: 'ArrowLeft' });
  expect(within(screen.getByRole('status')).getByText(/12.00/)).toBeDefined();

  fireEvent.click(screen.getByRole('button', { name: 'Semanal' }));
  const weeklyChart = screen.getByRole('group', { name: /Gasto semanal en/ });
  fireEvent.focus(weeklyChart);
  expect(within(screen.getByRole('status')).getByText('S/ 17.00')).toBeDefined();
});

it('triggers onSelectDay when clicking a daily bar with expenses or pressing Enter', () => {
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

it('triggers onSelectWeekRange when clicking a weekly bar or pressing Enter', () => {
  const onSelectWeek = vi.fn();
  const view = render(<ExpenseTrendChart points={points} monthLabel="enero de 2026" onSelectWeekRange={onSelectWeek} />);
  fireEvent.click(screen.getByRole('button', { name: 'Semanal' }));
  const bars = view.container.querySelectorAll('rect');
  expect(bars.length).toBe(1);

  fireEvent.click(bars[0]);
  expect(onSelectWeek).toHaveBeenCalledWith(1, 3);

  const weeklyChart = screen.getByRole('group', { name: /Gasto semanal en/ });
  fireEvent.focus(weeklyChart);
  expect(within(screen.getByRole('status')).getByText('Clic para ver movimientos de la semana →')).toBeDefined();
  fireEvent.keyDown(weeklyChart, { key: 'Enter' });
  expect(onSelectWeek).toHaveBeenCalledTimes(2);
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
