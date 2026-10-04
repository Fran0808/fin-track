// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { InstrumentForm } from '../src/components/instruments/InstrumentForm';
import { InstrumentsView } from '../src/components/views/InstrumentsView';
import { api } from '../src/services/api';
import type { FinancialInstrument, PageResponse, Transaction } from '../src/types';

const account: FinancialInstrument = { id: 1, type: 'BANK_ACCOUNT', alias: 'Principal', bank: 'BCP', lastFour: '1234', currency: 'PEN', active: true };
const card: FinancialInstrument = { ...account, id: 2, type: 'DEBIT_CARD', alias: 'Débito diario', linkedAccountId: 1 };
const emptyPage: PageResponse<Transaction> = { content: [], totalElements: 0, totalPages: 0, size: 10, number: 0, first: true, last: true, empty: true };
beforeEach(() => {
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', { configurable: true, value: function (this: HTMLDialogElement) { this.setAttribute('open', ''); } });
  Object.defineProperty(HTMLDialogElement.prototype, 'close', { configurable: true, value: function (this: HTMLDialogElement) { this.removeAttribute('open'); } });
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); });

it('creates an Other-bank product with only the optional four-digit suffix', async () => {
  const save = vi.spyOn(api, 'saveFinancialInstrument').mockResolvedValue({ ...account, bank: 'OTHER', institutionName: 'Cooperativa' });
  const saved = vi.fn();
  render(<InstrumentForm item={null} instruments={[]} onClose={vi.fn()} onSaved={saved} />);
  fireEvent.change(screen.getByLabelText('Alias'), { target: { value: ' Mi cuenta ' } });
  fireEvent.change(screen.getByLabelText('Banco'), { target: { value: 'OTHER' } });
  fireEvent.change(screen.getByLabelText('Nombre de la institución'), { target: { value: ' Cooperativa ' } });
  fireEvent.change(screen.getByLabelText('Últimos cuatro dígitos (opcional)'), { target: { value: '4321' } });
  fireEvent.click(screen.getByRole('button', { name: 'Guardar producto' }));
  await waitFor(() => expect(saved).toHaveBeenCalledOnce());
  expect(save).toHaveBeenCalledWith({
    type: 'BANK_ACCOUNT', alias: 'Mi cuenta', bank: 'OTHER', institutionName: 'Cooperativa',
    lastFour: '4321', active: true, linkedAccountId: null,
  }, undefined);
});

it('offers only active accounts from the same bank for debit and clears a stale link when changing bank', () => {
  render(<InstrumentForm item={card} instruments={[account, card, { ...account, id: 3, alias: 'BBVA', bank: 'BBVA' }, { ...account, id: 4, alias: 'Archivada', active: false }]} onClose={vi.fn()} onSaved={vi.fn()} />);
  expect(screen.getByRole('option', { name: 'Principal' })).toBeTruthy();
  expect(screen.queryByRole('option', { name: 'Archivada' })).toBeNull();
  fireEvent.change(screen.getByLabelText('Banco'), { target: { value: 'BBVA' } });
  expect((screen.getByLabelText('Cuenta vinculada (opcional)') as HTMLSelectElement).value).toBe('');
  expect(screen.queryByRole('option', { name: 'Principal' })).toBeNull();
});

it('keeps the form open with a clear error when saving fails', async () => {
  vi.spyOn(api, 'saveFinancialInstrument').mockRejectedValue(new Error('failure'));
  const saved = vi.fn();
  render(<InstrumentForm item={account} instruments={[account]} onClose={vi.fn()} onSaved={saved} />);
  fireEvent.click(screen.getByRole('button', { name: 'Guardar producto' }));
  expect(await screen.findByRole('alert')).toBeTruthy();
  expect(saved).not.toHaveBeenCalled();
});

it('archives only after confirmation and keeps archived history available', async () => {
  vi.spyOn(api, 'getFinancialInstruments').mockResolvedValue([account, { ...card, active: false }]);
  const save = vi.spyOn(api, 'saveFinancialInstrument').mockResolvedValue({ ...account, active: false });
  const transactions = vi.spyOn(api, 'getTransactions').mockResolvedValue(emptyPage);
  render(<InstrumentsView year={2026} month={10} revision={0} onSelectTransaction={vi.fn()} />);
  await screen.findByRole('button', { name: 'Archivar Principal' });
  fireEvent.click(screen.getByRole('button', { name: 'Archivar Principal' }));
  expect(save).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar archivo' }));
  await waitFor(() => expect(save).toHaveBeenCalledWith(expect.objectContaining({ active: false }), 1));
  fireEvent.change(screen.getByLabelText('Mostrar productos'), { target: { value: 'archived' } });
  fireEvent.click(await screen.findByRole('button', { name: 'Ver movimientos de Débito diario' }));
  await waitFor(() => expect(transactions).toHaveBeenCalledWith(expect.objectContaining({ financialInstrumentId: 2, page: 0, startDate: '2026-10-01T00:00:00' })));
});

it('loads the selected period and refreshes product movements after an assignment revision', async () => {
  vi.spyOn(api, 'getFinancialInstruments').mockResolvedValue([account]);
  const transactions = vi.spyOn(api, 'getTransactions').mockResolvedValue(emptyPage);
  const props = { year: 2026, month: 10, revision: 0, onSelectTransaction: vi.fn() };
  const view = render(<InstrumentsView {...props} />);
  fireEvent.click(await screen.findByRole('button', { name: 'Ver movimientos de Principal' }));
  await waitFor(() => expect(transactions).toHaveBeenCalledTimes(1));
  view.rerender(<InstrumentsView {...props} month={11} revision={1} />);
  await waitFor(() => expect(transactions).toHaveBeenLastCalledWith(expect.objectContaining({ financialInstrumentId: 1, startDate: '2026-11-01T00:00:00' })));
});

it('reports loading failures and lets the user retry', async () => {
  const get = vi.spyOn(api, 'getFinancialInstruments').mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce([]);
  render(<InstrumentsView year={2026} month={10} revision={0} onSelectTransaction={vi.fn()} />);
  await screen.findByRole('alert');
  fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
  expect(await screen.findByText(/No tienes productos/)).toBeTruthy();
  await waitFor(() => expect(get).toHaveBeenCalledTimes(2));
});
