// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { TransactionTable } from '../src/components/transactions/TransactionTable';
import type { TransactionPageResponse, Transaction, FlowType } from '../src/types';
import { formatCurrency } from '../src/utils';
const currency = (amount: number) => formatCurrency(amount).replace(/\s/g, ' ');

const expense: Transaction = { id: 1, amount: 0.10, flowType: 'EXPENSE', contactName: 'Synthetic shop',
  channel: 'UNKNOWN', transactionDate: '2026-01-01T12:00:00', transactionHash: 'summary-test', createdAt: '2026-01-01T12:00:00' };
const page: TransactionPageResponse = { content: [expense], totalElements: 12, totalPages: 12,
  number: 0, size: 1, first: true, last: false, empty: false,
  summary: { totalExpense: 123.45, totalIncome: 987.65, internalTransfersAmount: 42.10 } };
const props = { loading: false, onPageChange: vi.fn(), onSelectTransaction: vi.fn() };
afterEach(cleanup);

it('keeps filter totals stable across pages and distinguishes the page subtotal', () => {
  const { rerender } = render(<TransactionTable {...props} pageData={page} />);
  const totals = () => within(screen.getByRole('group', { name: 'Total del filtro' }));
  expect(totals().getByText(currency(123.45))).toBeDefined();
  expect(totals().getByText(currency(987.65))).toBeDefined();
  expect(totals().getByText(currency(42.10))).toBeDefined();
  expect((screen.getByText('Ver subtotal de esta página').closest('details') as HTMLDetailsElement).open).toBe(false);
  fireEvent.click(screen.getByText('Ver subtotal de esta página'));
  expect(within(screen.getByRole('group', { name: 'Esta página' })).getByText(currency(0.10))).toBeDefined();
  rerender(<TransactionTable {...props} pageData={{ ...page, number: 1, first: false,
    content: [{ ...expense, amount: 22.25 }] }} />);
  expect(totals().getByText(currency(123.45))).toBeDefined();
  expect(within(screen.getByRole('group', { name: 'Esta página' })).getByText(currency(22.25))).toBeDefined();
});

it.each(['EXPENSE', 'INCOME', 'INTERNAL_TRANSFER'] as FlowType[])('adapts the summary to the %s filter', flow => {
  render(<TransactionTable {...props} flowType={flow} pageData={{ ...page,
    content: [{ ...expense, flowType: flow, amount: 10.25 }] }} />);
  const total = screen.getByRole('group', { name: 'Total del filtro' });
  const label = { EXPENSE: 'Gastos', INCOME: 'Entradas', INTERNAL_TRANSFER: 'Transferencias propias' }[flow];
  expect(within(total).getByText(label)).toBeDefined();
  expect(total.querySelectorAll('dt')).toHaveLength(1);
  fireEvent.click(screen.getByText('Ver subtotal de esta página'));
  expect(within(screen.getByRole('group', { name: 'Esta página' })).getByText(currency(10.25))).toBeDefined();
});

it('shows zero filter totals for no matches and keeps loading separate from zero', () => {
  const { rerender } = render(<TransactionTable {...props} pageData={null} loading />);
  expect(screen.queryByRole('group', { name: 'Total del filtro' })).toBeNull();
  rerender(<TransactionTable {...props} flowType="INCOME" pageData={{ ...page, content: [], totalElements: 0,
    totalPages: 0, empty: true, summary: { totalExpense: 0, totalIncome: 0, internalTransfersAmount: 0 } }} />);
  expect(within(screen.getByRole('group', { name: 'Total del filtro' })).getByText(currency(0))).toBeDefined();
  expect(screen.getByText(/No se encontraron movimientos/)).toBeDefined();
});

it('does not present a page subtotal as a filter total when the API lacks summary', () => {
  render(<TransactionTable {...props} pageData={{ ...page, summary: undefined }} />);
  expect(screen.queryByRole('group', { name: 'Total del filtro' })).toBeNull();
  expect(screen.getByText(/Total del filtro no disponible/)).toBeDefined();
  fireEvent.click(screen.getByText('Ver subtotal de esta página'));
  expect(screen.getByRole('group', { name: 'Esta página' })).toBeDefined();
});
