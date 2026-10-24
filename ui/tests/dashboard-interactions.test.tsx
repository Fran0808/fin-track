// @vitest-environment jsdom
import { useState } from 'react';
import { act, cleanup, fireEvent, render, renderHook, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { TransactionDetailModal } from '../src/components/transactions/TransactionDetailModal';
import { TransactionsView } from '../src/components/views/TransactionsView';
import { useCategories } from '../src/hooks/useCategories';
import { api } from '../src/services/api';
import type { Category, FinancialInstrument, Transaction } from '../src/types';

const category: Category = { id: 1, name: 'Nueva categoría', active: true, system: false };
const card: FinancialInstrument = { id: 2, alias: 'Synthetic card', bank: 'BCP', type: 'DEBIT_CARD', currency: 'PEN', active: true };
const tx: Transaction = { id: 1, amount: 10, flowType: 'EXPENSE', contactName: 'Synthetic merchant', channel: 'YAPE', transactionDate: '2026-10-01T12:00:00', createdAt: '2026-10-01T12:00:00', transactionHash: 'synthetic', notes: 'Original' };
const page = { content: [tx], totalElements: 30, totalPages: 3, number: 0, size: 10, first: true, last: false, empty: false };

beforeEach(() => {
  window.history.replaceState({}, '', '/');
  vi.spyOn(api, 'getCategories').mockResolvedValue([category]);
  vi.spyOn(api, 'getFinancialInstruments').mockResolvedValue([card]);
  vi.spyOn(api, 'getInstrumentSuggestions').mockResolvedValue([card]);
  HTMLDialogElement.prototype.showModal = function () { this.open = true; };
  HTMLDialogElement.prototype.close = function () { this.open = false; this.dispatchEvent(new Event('close')); };
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

it('preserves notes, category and tags when assigning a product, then saves the draft', async () => {
  vi.spyOn(api, 'assignFinancialInstrument').mockResolvedValue({ ...tx, financialInstrument: card });
  const save = vi.spyOn(api, 'updateTransactionClassification').mockResolvedValue({ ...tx, financialInstrument: card, category: category.name, notes: 'Draft', tags: ['draft'] });
  function Detail() {
    const [transaction, setTransaction] = useState(tx);
    return <TransactionDetailModal transaction={transaction} onClose={() => {}} onUpdated={setTransaction} />;
  }
  render(<Detail />);
  await screen.findByRole('option', { name: category.name });
  fireEvent.change(screen.getByLabelText('Categoría'), { target: { value: category.name } });
  fireEvent.change(screen.getByLabelText('Notas personales'), { target: { value: 'Draft' } });
  fireEvent.change(screen.getByLabelText('Etiquetas (#tags)'), { target: { value: 'draft' } });
  fireEvent.click(screen.getByRole('button', { name: 'Agregar' }));
  fireEvent.click(await screen.findByRole('button', { name: /Confirmar Synthetic card/ }));
  await screen.findByText(card.alias);
  expect((screen.getByLabelText('Notas personales') as HTMLTextAreaElement).value).toBe('Draft');
  expect((screen.getByLabelText('Categoría') as HTMLSelectElement).value).toBe(category.name);
  expect(screen.getByText('#draft')).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: 'Guardar clasificación' }));
  await waitFor(() => expect(save).toHaveBeenCalledWith(tx.id, { category: category.name, notes: 'Draft', tags: ['draft'] }));
  await screen.findByText('Cambios guardados');
});

it('reloads categories on each detail opening and resets the draft for a different movement', async () => {
  const view = render(<TransactionDetailModal transaction={tx} onClose={() => {}} />);
  await screen.findByRole('option', { name: category.name });
  fireEvent.change(screen.getByLabelText('Notas personales'), { target: { value: 'Draft' } });
  view.rerender(<TransactionDetailModal transaction={null} onClose={() => {}} />);
  vi.mocked(api.getCategories).mockResolvedValue([{ ...category, name: 'Renombrada' }]);
  view.rerender(<TransactionDetailModal transaction={{ ...tx, id: 3, notes: 'Different movement' }} onClose={() => {}} />);
  await screen.findByRole('option', { name: 'Renombrada' });
  expect(screen.queryByRole('option', { name: category.name })).toBeNull();
  expect((screen.getByLabelText('Notas personales') as HTMLTextAreaElement).value).toBe('Different movement');
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

