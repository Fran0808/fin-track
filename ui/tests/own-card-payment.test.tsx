// @vitest-environment jsdom
import { useState } from 'react';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
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
  vi.spyOn(api, 'getFinancialInstruments').mockResolvedValue([]);
  vi.spyOn(api, 'getInstrumentSuggestions').mockResolvedValue([]);
  HTMLDialogElement.prototype.showModal = function () { this.open = true; };
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); });

it('confirms an unspecified payment from any provider only after confirmation and preserves the category draft', async () => {
  const repair = vi.spyOn(api, 'markOwnCardPayment').mockResolvedValue({ ...payment, flowType: 'INTERNAL_TRANSFER', operationType: 'OWN_CARD_PAYMENT' });
  function Detail() {
    const [transaction, setTransaction] = useState(payment);
    return <TransactionDetailModal transaction={transaction} onClose={vi.fn()} onUpdated={setTransaction} />;
  }
  render(<Detail />);
  await screen.findByRole('option', { name: 'Ocio' });
  fireEvent.change(screen.getByLabelText('Categoría'), { target: { value: 'Ocio' } });
  expect(repair).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: 'Es un pago de mi tarjeta' }));
  expect(repair).not.toHaveBeenCalled();
  expect(screen.getByText(/Las compras y los pagos a tarjetas de otras personas/)).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar pago de mi tarjeta' }));
  await screen.findByText('No suma gastos ni entradas y no afecta el flujo neto.');
  expect(repair).toHaveBeenCalledExactlyOnceWith(payment.id);
  expect(screen.getAllByText('Pago de tarjeta propia')).toHaveLength(2);
  expect(screen.queryByRole('button', { name: 'Es un pago de mi tarjeta' })).toBeNull();
  expect((screen.getByLabelText('Categoría') as HTMLSelectElement).value).toBe('Ocio');
});

it('keeps the expense unchanged on failure and permits retry', async () => {
  const repair = vi.spyOn(api, 'markOwnCardPayment').mockRejectedValueOnce(new Error('No disponible'))
    .mockResolvedValue({ ...payment, flowType: 'INTERNAL_TRANSFER', operationType: 'OWN_CARD_PAYMENT' });
  const updated = vi.fn();
  render(<TransactionDetailModal transaction={payment} onClose={vi.fn()} onUpdated={updated} />);
  fireEvent.click(screen.getByRole('button', { name: 'Es un pago de mi tarjeta' }));
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar pago de mi tarjeta' }));
  expect(await screen.findByRole('alert')).toHaveProperty('textContent', 'No disponible');
  expect(updated).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar pago de mi tarjeta' }));
  await waitFor(() => expect(updated).toHaveBeenCalledWith({ ...payment, flowType: 'INTERNAL_TRANSFER', operationType: 'OWN_CARD_PAYMENT' }));
  expect(repair).toHaveBeenCalledTimes(2);
});

it('does not infer meaning from bank text, rejects income and labels normalized rows without an expense sign', () => {
  const view = render(<TransactionDetailModal transaction={{ ...payment, operationType: undefined, contactName: 'Pago Tarjeta Crédito BCP' }} onClose={vi.fn()} onUpdated={vi.fn()} />);
  expect(screen.queryByRole('button', { name: 'Es un pago de mi tarjeta' })).toBeNull();
  view.rerender(<TransactionDetailModal transaction={{ ...payment, flowType: 'INCOME' }} onClose={vi.fn()} onUpdated={vi.fn()} />);
  expect(screen.queryByRole('button', { name: 'Es un pago de mi tarjeta' })).toBeNull();
  view.unmount();
  render(<TransactionTable pageData={{ content: [{ ...payment, flowType: 'INTERNAL_TRANSFER', operationType: 'OWN_CARD_PAYMENT' }],
    totalElements: 1, totalPages: 1, number: 0, size: 10, first: true, last: true, empty: false }}
    loading={false} onPageChange={vi.fn()} onSelectTransaction={vi.fn()} />);
  expect(screen.getByText('Pago de tarjeta propia')).toBeTruthy();
  expect(screen.getByText('S/ 81.25').textContent?.replace(/\s/g, ' ')).toBe('S/ 81.25');
});

it('cancels confirmation without changing an expense and permits describing an old neutral transfer', () => {
  const repair = vi.spyOn(api, 'markOwnCardPayment');
  render(<TransactionDetailModal transaction={{ ...payment, flowType: 'INTERNAL_TRANSFER' }} onClose={vi.fn()} onUpdated={vi.fn()} />);
  fireEvent.click(screen.getByRole('button', { name: 'Es un pago de mi tarjeta' }));
  fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }));
  expect(screen.queryByRole('button', { name: 'Confirmar pago de mi tarjeta' })).toBeNull();
  expect(repair).not.toHaveBeenCalled();
  expect(getTransactionFlowLabel({ ...payment, flowType: 'INTERNAL_TRANSFER', contactName: 'Pago Tarjeta Crédito BCP' })).toBe('Transferencia propia');
});

it('requires normalized operation evidence from the PATCH response', async () => {
  const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(new Response(JSON.stringify({ ...payment, flowType: 'INTERNAL_TRANSFER' }), { status: 200 }));
  await expect(api.markOwnCardPayment(payment.id)).rejects.toThrow('La API no confirmó el tipo de operación');
  fetch.mockResolvedValueOnce(new Response(JSON.stringify({ ...payment, flowType: 'INTERNAL_TRANSFER', operationType: 'OWN_CARD_PAYMENT' }), { status: 200 }));
  await expect(api.markOwnCardPayment(payment.id)).resolves.toMatchObject({ operationType: 'OWN_CARD_PAYMENT' });
});
