// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { TransactionDetailModal } from '../src/components/transactions/TransactionDetailModal';
import { TransactionTable } from '../src/components/transactions/TransactionTable';
import { api } from '../src/services/api';
import type { Transaction } from '../src/types';
import { getTransactionFlowLabel } from '../src/utils';

const payment: Transaction = {
  id: 7, contactName: 'Reference only', amount: 81.25, flowType: 'EXPENSE', operationType: 'UNKNOWN',
  channel: 'EXAMPLE_WALLET', transactionDate: '2026-01-02T12:00:00',
  transactionHash: 'synthetic-payment', createdAt: '2026-01-02T12:00:00',
};
beforeEach(() => {
  vi.spyOn(api, 'getCategories').mockResolvedValue([{ id: 1, name: 'Ocio', active: true, system: false }]);
  HTMLDialogElement.prototype.showModal = function () { this.open = true; };
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); });

it('omits the manual own-card payment button from the movement detail modal', async () => {
  const view = render(<TransactionDetailModal transaction={payment} onClose={vi.fn()} onUpdated={vi.fn()} />);
  await screen.findByRole('option', { name: 'Ocio' });
  expect(screen.queryByRole('button', { name: 'Es un pago de mi tarjeta' })).toBeNull();
  view.rerender(<TransactionDetailModal transaction={{ ...payment, flowType: 'INTERNAL_TRANSFER' }} onClose={vi.fn()} onUpdated={vi.fn()} />);
  expect(screen.queryByRole('button', { name: 'Es un pago de mi tarjeta' })).toBeNull();
});

it('displays automatically detected own-card payments as neutral in the modal and table', () => {
  const ownPayment: Transaction = { ...payment, flowType: 'INTERNAL_TRANSFER', operationType: 'OWN_CARD_PAYMENT' };
  const view = render(<TransactionDetailModal transaction={ownPayment} onClose={vi.fn()} onUpdated={vi.fn()} />);
  expect(screen.getByText('Pago de tarjeta propia')).toBeTruthy();
  expect(screen.getByText('No suma gastos ni entradas y no afecta el flujo neto.')).toBeTruthy();
  view.unmount();

  render(<TransactionTable pageData={{ content: [ownPayment],
    totalElements: 1, totalPages: 1, number: 0, size: 10, first: true, last: true, empty: false }}
    loading={false} onPageChange={vi.fn()} onSelectTransaction={vi.fn()} />);
  expect(screen.getByText('Pago de tarjeta propia')).toBeTruthy();
  expect(screen.getByRole('cell', { name: /S\/\s81\.25/ }).textContent?.replace(/\s/g, ' ')).toBe('S/ 81.25');
});

it('does not infer meaning from bank text for unspecified neutral transfers', () => {
  expect(getTransactionFlowLabel({ ...payment, flowType: 'INTERNAL_TRANSFER', contactName: 'Pago Tarjeta Crédito BCP' })).toBe('Transferencia propia');
});
