// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { StrictMode } from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ManualTransactionModal } from '../src/components/transactions/ManualTransactionModal';
import { TransactionsView } from '../src/components/views/TransactionsView';
import { api } from '../src/services/api';
import type { Transaction } from '../src/types';

const manual: Transaction = { id: 1, captureOrigin: 'MANUAL', amount: 12.34, flowType: 'EXPENSE',
  contactName: 'Synthetic manual', channel: 'CASH', transactionDate: '2026-01-31T12:00:00', transactionHash: 'manual:synthetic', createdAt: '2026-02-01T12:00:00' };
beforeEach(() => {
  HTMLDialogElement.prototype.showModal = function () { this.open = true; };
  HTMLDialogElement.prototype.close = function () { this.open = false; };
  vi.spyOn(api, 'getCategories').mockResolvedValue([{ id: 7, name: 'Synthetic category', active: true, system: false }]);
  vi.spyOn(api, 'createManualTransaction').mockResolvedValue(manual);
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

it('keeps the dialog open during development effect replay and still dismisses with Escape', async () => {
  HTMLDialogElement.prototype.close = function () {
    if (!this.open) return;
    this.open = false;
    setTimeout(() => this.dispatchEvent(new Event('close')), 0);
  };
  render(<StrictMode><TransactionsView pageData={null} loading={false} filters={{}} onFilterChange={vi.fn()} onPageChange={vi.fn()} onSelectTransaction={vi.fn()} /></StrictMode>);
  fireEvent.click(screen.getByRole('button', { name: 'Registrar manualmente' }));
  await new Promise(resolve => setTimeout(resolve, 30));
  const dialog = screen.getByRole('dialog', { name: 'Registro manual' }) as HTMLDialogElement;
  expect(dialog.open).toBe(true);
  dialog.close();
  await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Registro manual' })).toBeNull());
  expect(api.createManualTransaction).not.toHaveBeenCalled();
});

it('opens manual entry when randomUUID is unavailable on an HTTP origin', async () => {
  const browserCrypto = globalThis.crypto;
  vi.stubGlobal('crypto', { getRandomValues: browserCrypto.getRandomValues.bind(browserCrypto) });
  render(<TransactionsView pageData={null} loading={false} filters={{}} onFilterChange={vi.fn()} onPageChange={vi.fn()} onSelectTransaction={vi.fn()} />);
  fireEvent.click(screen.getByRole('button', { name: 'Registrar manualmente' }));
  expect(await screen.findByRole('dialog', { name: 'Registro manual' })).toBeTruthy();
  expect(api.createManualTransaction).not.toHaveBeenCalled();
});

function fill() {
  fireEvent.change(screen.getByLabelText('Importe (S/)'), { target: { value: '12,34' } });
  fireEvent.change(screen.getByLabelText('Comercio o concepto'), { target: { value: ' Synthetic manual ' } });
  fireEvent.change(screen.getByLabelText('Fecha y hora del movimiento'), { target: { value: '2026-01-31T12:00' } });
}

it('reviews a decimal manual entry and does not write until confirmation', async () => {
  const created = vi.fn(); render(<ManualTransactionModal onClose={vi.fn()} onCreated={created} />);
  fill(); fireEvent.click(screen.getByRole('button', { name: 'Revisar movimiento' }));
  expect(screen.getByRole('heading', { name: 'Revisar registro manual' })).toBeTruthy();
  expect(api.createManualTransaction).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar registro' }));
  await waitFor(() => expect(created).toHaveBeenCalledWith(manual));
  expect(api.createManualTransaction).toHaveBeenCalledWith(expect.objectContaining({ amount: '12.34', flowType: 'EXPENSE', contactName: 'Synthetic manual', paymentMethod: 'CASH', categoryId: null, transactionDate: '2026-01-31T12:00:00' }));
});

it.each(['0', '-1', '1.234', '100000000', '12,3.4'])('rejects amount %s without silently rounding', async value => {
  render(<ManualTransactionModal onClose={vi.fn()} onCreated={vi.fn()} />); fill();
  fireEvent.change(screen.getByLabelText('Importe (S/)'), { target: { value } });
  fireEvent.click(screen.getByRole('button', { name: 'Revisar movimiento' }));
  expect(await screen.findByRole('alert')).toHaveProperty('textContent', expect.stringContaining('dos decimales'));
  expect(api.createManualTransaction).not.toHaveBeenCalled();
});

it('retains the request identity and values after an uncertain save failure', async () => {
  vi.mocked(api.createManualTransaction).mockRejectedValueOnce(new Error('Connection interrupted'));
  const created = vi.fn(); render(<ManualTransactionModal onClose={vi.fn()} onCreated={created} />); fill();
  fireEvent.click(screen.getByRole('button', { name: 'Revisar movimiento' }));
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar registro' }));
  await screen.findByRole('alert'); const original = vi.mocked(api.createManualTransaction).mock.calls[0][0];
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar registro' }));
  await waitFor(() => expect(created).toHaveBeenCalledOnce());
  expect(vi.mocked(api.createManualTransaction).mock.calls[1][0]).toEqual(original);
});

it('cancels creation without writing and restores the invoking control on unmount', () => {
  const opener = document.createElement('button'); document.body.append(opener); opener.focus();
  const close = vi.fn(); const view = render(<ManualTransactionModal onClose={close} onCreated={vi.fn()} />);
  fill(); fireEvent.click(screen.getByRole('button', { name: 'Cerrar registro manual' }));
  expect(close).toHaveBeenCalledOnce(); expect(api.createManualTransaction).not.toHaveBeenCalled();
  view.unmount(); expect(document.activeElement).toBe(opener); opener.remove();
});
