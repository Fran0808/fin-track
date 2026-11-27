// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { HomeView } from '../src/components/views/HomeView';
import type { PeriodAnalytics } from '../src/types';

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

const analytics: PeriodAnalytics = {
  monthlyExpense: 420.5,
  monthlyIncome: 1200,
  previousPeriodExpense: 380,
  previousPeriodIncome: 1100,
  totalMovements: 9,
  dailyExpenses: [{ day: 1, amount: 420.5 }],
  categoryBreakdown: [
    { category: 'Comida', amount: 280.5, percentage: 66.7, count: 5 },
    { category: null, amount: 140, percentage: 33.3, count: 2 },
  ],
  channelBreakdown: [],
  topMerchants: [],
};

it('supports 1-click drill-downs from Home summary cards, top category, uncategorized banner, and quick manual add', () => {
  const onSelectFlow = vi.fn();
  const onSelectCategory = vi.fn();
  const onOpenManualTransaction = vi.fn();

  render(
    <HomeView
      analytics={analytics}
      recentTransactions={[]}
      loadingSummary={false}
      loadingRecent={false}
      onNavigateToTransactions={vi.fn()}
      onSelectTransaction={vi.fn()}
      onSelectFlow={onSelectFlow}
      onSelectCategory={onSelectCategory}
      onOpenManualTransaction={onOpenManualTransaction}
      selectedYear={2026}
      selectedMonth={11}
      animateIntro={false}
      onIntroComplete={vi.fn()}
    />
  );

  fireEvent.click(screen.getByRole('button', { name: 'Ver gastos del mes' }));
  expect(onSelectFlow).toHaveBeenCalledWith('EXPENSE');

  fireEvent.click(screen.getByRole('button', { name: 'Ver entradas del mes' }));
  expect(onSelectFlow).toHaveBeenCalledWith('INCOME');

  fireEvent.click(screen.getByRole('button', { name: /Mayor rubro:/i }));
  expect(onSelectCategory).toHaveBeenCalledWith('Comida');

  fireEvent.click(screen.getByRole('button', { name: 'Clasificar ahora' }));
  expect(onSelectCategory).toHaveBeenCalledWith(null);

  fireEvent.click(screen.getByRole('button', { name: 'Agregar movimiento' }));
  expect(onOpenManualTransaction).toHaveBeenCalledOnce();
});
