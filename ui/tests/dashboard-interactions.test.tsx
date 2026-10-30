// @vitest-environment jsdom
import { useState } from 'react';
import { act, cleanup, fireEvent, render, renderHook, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { TransactionDetailModal } from '../src/components/transactions/TransactionDetailModal';
import { HomeView } from '../src/components/views/HomeView';
import { TransactionsView } from '../src/components/views/TransactionsView';
import { useCategories } from '../src/hooks/useCategories';
import { api } from '../src/services/api';
import type { Category, PeriodAnalytics, Transaction } from '../src/types';

const category: Category = { id: 1, name: 'Nueva categoría', active: true, system: false };
const tx: Transaction = { id: 1, amount: 10, flowType: 'EXPENSE', contactName: 'Synthetic merchant', channel: 'YAPE', transactionDate: '2026-10-01T12:00:00', createdAt: '2026-10-01T12:00:00', transactionHash: 'synthetic' };
const page = { content: [tx], totalElements: 30, totalPages: 3, number: 0, size: 10, first: true, last: false, empty: false };

beforeEach(() => {
  window.history.replaceState({}, '', '/');
  vi.spyOn(api, 'getCategories').mockResolvedValue([category]);
  HTMLDialogElement.prototype.showModal = function () { this.open = true; };
  HTMLDialogElement.prototype.close = function () { this.open = false; this.dispatchEvent(new Event('close')); };
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

it('saves the category classification for a movement', async () => {
  const save = vi.spyOn(api, 'updateTransactionClassification').mockResolvedValue({ ...tx, category: category.name });
  function Detail() {
    const [transaction, setTransaction] = useState(tx);
    return <TransactionDetailModal transaction={transaction} onClose={() => {}} onUpdated={setTransaction} />;
  }
  render(<Detail />);
  await screen.findByRole('option', { name: category.name });
  expect(screen.queryByRole('button', { name: 'Guardar cambios' })).toBeNull();
  expect(screen.queryByText('Clasificación')).toBeNull();
  fireEvent.change(screen.getByLabelText('Categoría'), { target: { value: category.name } });
  expect((screen.getByLabelText('Categoría') as HTMLSelectElement).value).toBe(category.name);
  fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));
  await waitFor(() => expect(save).toHaveBeenCalledWith(tx.id, { category: category.name }));
  await screen.findByText('Cambios guardados');
  expect(screen.queryByRole('button', { name: 'Guardar cambios' })).toBeNull();
});

it('hides save when reverting the selection and opens a rule from the accessible icon with the current draft', async () => {
  const createRule = vi.fn();
  render(<TransactionDetailModal transaction={tx} onClose={vi.fn()} onCreateRule={createRule} />);
  await screen.findByRole('option', { name: category.name });
  const select = screen.getByLabelText('Categoría');
  fireEvent.change(select, { target: { value: category.name } });
  expect(screen.getByRole('button', { name: 'Guardar cambios' })).toBeTruthy();
  const ruleIcon = screen.getByRole('button', { name: 'Crear regla para este comercio' });
  expect(ruleIcon.textContent).toBe('');
  expect(ruleIcon.getAttribute('title')).toBe('Crear regla para este comercio');
  fireEvent.click(ruleIcon);
  expect(createRule).toHaveBeenCalledWith({ matchText: tx.contactName, categoryName: category.name });
  fireEvent.change(select, { target: { value: '' } });
  expect(screen.queryByRole('button', { name: 'Guardar cambios' })).toBeNull();
});

it('retains the changed category and save action after failure so it can be retried', async () => {
  const save = vi.spyOn(api, 'updateTransactionClassification').mockRejectedValueOnce(new Error('Synthetic unavailable'))
    .mockResolvedValue({ ...tx, category: category.name });
  render(<TransactionDetailModal transaction={tx} onClose={vi.fn()} />);
  await screen.findByRole('option', { name: category.name });
  fireEvent.change(screen.getByLabelText('Categoría'), { target: { value: category.name } });
  fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));
  expect(await screen.findByRole('alert')).toHaveProperty('textContent', 'Synthetic unavailable');
  expect((screen.getByLabelText('Categoría') as HTMLSelectElement).value).toBe(category.name);
  fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));
  await screen.findByText('Cambios guardados');
  expect(save).toHaveBeenCalledTimes(2);
  expect(screen.queryByRole('button', { name: 'Guardar cambios' })).toBeNull();
});

it('omits the internal transfers note under Medios de pago in HomeView', () => {
  const analytics: PeriodAnalytics = {
    periodName: 'Octubre 2026', monthlyExpense: 1322.66, monthlyIncome: 232.76, internalTransfersAmount: 1075.16,
    previousPeriodExpense: null, comparisonThroughDay: null, dailyExpenses: [], totalMovements: 5,
    lastExpenseMerchant: 'Synthetic', lastExpenseAmount: 10, lastExpenseDate: '2026-10-01T12:00:00',
    topChannel: 'TARJETA_CREDITO_BCP', topChannelAmount: 1114.66, topChannelPercentage: 84.3,
    channelBreakdown: [{ channel: 'TARJETA_CREDITO_BCP', cardLast4: '3127', displayName: 'BCP Crédito ··3127', amount: 1114.66, percentage: 84.3, count: 3 }],
    topMerchants: [], categoryBreakdown: [],
  };
  render(<HomeView analytics={analytics} recentTransactions={[tx]} loading={false} onNavigateToTransactions={() => {}} onSelectTransaction={() => {}} selectedYear={2026} selectedMonth={10} />);
  expect(screen.getByText('BCP Crédito ··3127')).toBeTruthy();
  expect(screen.queryByText(/en transferencias propias, excluidas de gastos/i)).toBeNull();
});

it('displays Gastos del mes first and Entradas del mes second with previous-month variations and relevant context in HomeView', () => {
  const analytics: PeriodAnalytics = {
    periodName: 'Octubre 2026', monthlyExpense: 2850, monthlyIncome: 4500, internalTransfersAmount: 0,
    previousPeriodExpense: 2544.64, previousPeriodIncome: 4166.67, comparisonThroughDay: null,
    dailyExpenses: [{ day: 1, amount: 1425, cumulativeAmount: 1425 }, { day: 2, amount: 1425, cumulativeAmount: 2850 }],
    totalMovements: 5,
    lastExpenseMerchant: 'Synthetic', lastExpenseAmount: 10, lastExpenseDate: '2026-10-01T12:00:00',
    topChannel: 'YAPE', topChannelAmount: 2850, topChannelPercentage: 100,
    channelBreakdown: [], topMerchants: [],
    categoryBreakdown: [{ category: 'Comida y bebidas', amount: 1200, percentage: 42.1, count: 3 }],
  };
  render(<HomeView analytics={analytics} recentTransactions={[tx]} loading={false} onNavigateToTransactions={() => {}} onSelectTransaction={() => {}} selectedYear={2026} selectedMonth={10} animateIntro={false} onIntroComplete={() => {}} />);
  const headings = screen.getAllByText(/Gastos del mes|Entradas del mes/).map((el) => el.textContent);
  expect(headings).toEqual(['Gastos del mes', 'Entradas del mes']);
  expect(screen.getByText('+12.0% vs. mes ant.')).toBeTruthy();
  expect(screen.getByText('+8.0% vs. mes ant.')).toBeTruthy();
  expect(screen.getByText('Comida y bebidas (42%)')).toBeTruthy();
  expect(screen.queryByText('Balance del mes')).toBeNull();
  expect(screen.queryByText('Tasa de ahorro')).toBeNull();
});

it('reloads categories on each detail opening and resets the draft for a different movement', async () => {
  const view = render(<TransactionDetailModal transaction={tx} onClose={() => {}} />);
  await screen.findByRole('option', { name: category.name });
  fireEvent.change(screen.getByLabelText('Categoría'), { target: { value: category.name } });
  view.rerender(<TransactionDetailModal transaction={null} onClose={() => {}} />);
  vi.mocked(api.getCategories).mockResolvedValue([{ ...category, name: 'Renombrada' }]);
  view.rerender(<TransactionDetailModal transaction={{ ...tx, id: 3 }} onClose={() => {}} />);
  await screen.findByRole('option', { name: 'Renombrada' });
  expect(screen.queryByRole('option', { name: category.name })).toBeNull();
  expect((screen.getByLabelText('Categoría') as HTMLSelectElement).value).toBe('');
});

it('distinguishes a failed category request from an empty catalog and permits retry', async () => {
  vi.mocked(api.getCategories).mockRejectedValueOnce(new Error('Catalog unavailable')).mockResolvedValue([]);
  const { result } = renderHook(() => useCategories());
  await waitFor(() => expect(result.current.error).toBe('Catalog unavailable'));
  expect(result.current.options).toEqual([]);
  await act(async () => result.current.refresh());
  expect(result.current.error).toBeNull();
  expect(result.current.options).toEqual([]);
});

it('reports a failed CSV export without downloading a partial page and supports retry', async () => {
  const csv = new Blob(['complete export'], { type: 'text/csv' });
  const exportCsv = vi.spyOn(api, 'exportTransactionsCsv').mockRejectedValueOnce(new Error('Offline')).mockResolvedValue(csv);
  const download = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
  const create = vi.fn(() => 'blob:synthetic');
  vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: create, revokeObjectURL: vi.fn() }));
  const filters = { search: 'Synthetic', startDate: '2026-10-01T00:00:00', endDate: '2026-10-31T23:59:59' };
  render(<TransactionsView pageData={page} loading={false} filters={filters} onFilterChange={() => {}} onPageChange={() => {}} onSelectTransaction={() => {}} />);
  fireEvent.click(screen.getByRole('button', { name: 'Exportar CSV' }));
  expect(await screen.findByRole('alert')).toHaveProperty('textContent', expect.stringContaining('No se pudo exportar'));
  expect(download).not.toHaveBeenCalled();
  expect(create).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: 'Exportar CSV' }));
  await waitFor(() => expect(download).toHaveBeenCalledOnce());
  expect(create).toHaveBeenCalledWith(csv);
  expect(exportCsv).toHaveBeenLastCalledWith(filters);
  expect(screen.queryByRole('alert')).toBeNull();
});


it('displays Balance neto with surplus or deficit and exact decimal difference in HomeView', () => {
  const analytics: PeriodAnalytics = {
    periodName: 'Octubre 2026', monthlyExpense: 1200.50, monthlyIncome: 2000.75, internalTransfersAmount: 0,
    previousPeriodExpense: 1000, previousPeriodIncome: 1500, comparisonThroughDay: null,
    dailyExpenses: [], totalMovements: 4,
    lastExpenseMerchant: 'Shop', lastExpenseAmount: 50, lastExpenseDate: '2026-10-01T12:00:00',
    topChannel: 'YAPE', topChannelAmount: 1200.50, topChannelPercentage: 100,
    channelBreakdown: [], topMerchants: [], categoryBreakdown: [],
  };
  const { rerender } = render(
    <HomeView analytics={analytics} recentTransactions={[]} loading={false}
      onNavigateToTransactions={vi.fn()} onSelectTransaction={vi.fn()}
      selectedYear={2026} selectedMonth={10} animateIntro={false} onIntroComplete={vi.fn()} />
  );
  expect(screen.getByText('Balance neto')).toBeTruthy();
  expect(screen.getByText('A favor')).toBeTruthy();
  expect(screen.getByText('+ S/ 800.25')).toBeTruthy();
  expect(screen.getByText('Mes anterior: + S/ 500.00')).toBeTruthy();
  expect(screen.getByText('Superávit en el período')).toBeTruthy();

  // Test deficit
  rerender(
    <HomeView analytics={{ ...analytics, monthlyExpense: 2500, monthlyIncome: 2000 }} recentTransactions={[]} loading={false}
      onNavigateToTransactions={vi.fn()} onSelectTransaction={vi.fn()}
      selectedYear={2026} selectedMonth={10} animateIntro={false} onIntroComplete={vi.fn()} />
  );
  expect(screen.getByText('En contra')).toBeTruthy();
  expect(screen.getByText('− S/ 500.00')).toBeTruthy();
  expect(screen.getByText('Salidas superan a las entradas')).toBeTruthy();
});

it('renders semantic category color chips in HomeView recent movements and TransactionTable', () => {
  const categorizedTx: Transaction = { ...tx, id: 11, category: 'ALIMENTACION' };
  const uncategorizedTx: Transaction = { ...tx, id: 12, contactName: 'Tienda sin rubro', category: null };
  const analytics: PeriodAnalytics = {
    periodName: 'Octubre 2026', monthlyExpense: 100, monthlyIncome: 0, internalTransfersAmount: 0,
    previousPeriodExpense: null, comparisonThroughDay: null, dailyExpenses: [], totalMovements: 2,
    lastExpenseMerchant: 'Synthetic', lastExpenseAmount: 10, lastExpenseDate: '2026-10-01T12:00:00',
    topChannel: 'YAPE', topChannelAmount: 100, topChannelPercentage: 100,
    channelBreakdown: [], topMerchants: [], categoryBreakdown: [],
  };
  render(
    <>
      <HomeView analytics={analytics} recentTransactions={[categorizedTx]} loading={false} onNavigateToTransactions={() => {}} onSelectTransaction={() => {}} selectedYear={2026} selectedMonth={10} animateIntro={false} onIntroComplete={() => {}} />
      <TransactionsView pageData={{ ...page, content: [categorizedTx, uncategorizedTx] }} loading={false} filters={{}} onFilterChange={() => {}} onPageChange={() => {}} onSelectTransaction={() => {}} />
    </>
  );
  expect(screen.getAllByText('Alimentación y bebidas').length).toBeGreaterThanOrEqual(2);
  expect(screen.getAllByText('Sin categoría').length).toBeGreaterThanOrEqual(1);
});

it('navigates to movements when clicking a payment channel in HomeView', () => {
  const onSelectChannel = vi.fn();
  const analytics: PeriodAnalytics = {
    periodName: 'Octubre 2026', monthlyExpense: 100, monthlyIncome: 0, internalTransfersAmount: 0,
    previousPeriodExpense: null, comparisonThroughDay: null, dailyExpenses: [], totalMovements: 1,
    lastExpenseMerchant: 'Synthetic', lastExpenseAmount: 10, lastExpenseDate: '2026-10-01T12:00:00',
    topChannel: 'YAPE', topChannelAmount: 100, topChannelPercentage: 100,
    channelBreakdown: [{ channel: 'YAPE', amount: 100, percentage: 100, count: 1 }],
    topMerchants: [], categoryBreakdown: [],
  };
  render(<HomeView analytics={analytics} recentTransactions={[]} loading={false} onNavigateToTransactions={vi.fn()} onSelectTransaction={vi.fn()} onSelectChannel={onSelectChannel} selectedYear={2026} selectedMonth={10} animateIntro={false} onIntroComplete={vi.fn()} />);
  fireEvent.click(screen.getByRole('button', { name: 'Ver movimientos de Yape' }));
  expect(onSelectChannel).toHaveBeenCalledWith('YAPE');
});
