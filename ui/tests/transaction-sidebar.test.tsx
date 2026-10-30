// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { TransactionDetailModal } from '../src/components/transactions/TransactionDetailModal';
import { TransactionTable } from '../src/components/transactions/TransactionTable';
import { api } from '../src/services/api';
import type { Transaction } from '../src/types';

const tx: Transaction = { id: 1, amount: 12.34, flowType: 'EXPENSE', captureOrigin: 'UNKNOWN', contactName: 'Synthetic sidebar store', channel: 'CASH', transactionDate: '2026-10-01T12:00:00', createdAt: '2026-10-01T12:00:00', transactionHash: 'synthetic-sidebar' };
let media: { matches: boolean; addEventListener: ReturnType<typeof vi.fn>; removeEventListener: ReturnType<typeof vi.fn> };
beforeEach(() => {
  media = { matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() };
  vi.stubGlobal('matchMedia', () => media);
  HTMLDialogElement.prototype.show = vi.fn(function () { this.open = true; });
  HTMLDialogElement.prototype.showModal = vi.fn(function () { this.open = true; });
  HTMLDialogElement.prototype.close = vi.fn(function () { this.open = false; setTimeout(() => this.dispatchEvent(new Event('close')), 0); });
  vi.spyOn(api, 'getCategories').mockResolvedValue([]);
  vi.spyOn(api, 'getTransactionOrigin').mockResolvedValue({ origin: 'UNKNOWN', registeredAt: null, operationReference: null });
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

it('opens a nonmodal docked sidebar on desktop and dismisses it with Escape, not a content click', async () => {
  const close = vi.fn();
  render(<TransactionDetailModal transaction={tx} presentation="sidebar" onClose={close} />);
  const panel = screen.getByRole('dialog', { name: 'Detalle del movimiento' });
  expect(panel.getAttribute('aria-modal')).toBe('false');
  expect(HTMLDialogElement.prototype.show).toHaveBeenCalledOnce();
  expect(HTMLDialogElement.prototype.showModal).not.toHaveBeenCalled();
  fireEvent.click(panel); expect(close).not.toHaveBeenCalled();
  const select = screen.getByLabelText('Categoría');
  vi.stubGlobal('CSS', { supports: () => true });
  vi.spyOn(select, 'matches').mockImplementation(selector => selector === ':open');
  fireEvent.keyDown(select.querySelector('option')!, { key: 'Escape' });
  expect(HTMLDialogElement.prototype.close).not.toHaveBeenCalled();
  fireEvent.keyDown(panel, { key: 'Escape' });
  await act(async () => { await new Promise(resolve => setTimeout(resolve, 10)); });
  expect(close).toHaveBeenCalledOnce();
});

it('keeps the centered modal as the default for Home', () => {
  render(<TransactionDetailModal transaction={tx} onClose={vi.fn()} />);
  const dialog = screen.getByRole('dialog', { name: 'Detalle del movimiento' });
  expect(dialog.getAttribute('aria-modal')).toBe('true');
  expect(dialog.className).toContain('m-auto');
  expect(HTMLDialogElement.prototype.showModal).toHaveBeenCalledOnce();
});

it('changes the sidebar to a modal drawer on narrow screens without interpreting resize as user dismissal', async () => {
  const close = vi.fn();
  render(<TransactionDetailModal transaction={tx} presentation="sidebar" onClose={close} />);
  media.matches = false;
  act(() => media.addEventListener.mock.calls[0][1]());
  await act(async () => { await new Promise(resolve => setTimeout(resolve, 10)); });
  expect(screen.getByRole('dialog').getAttribute('aria-modal')).toBe('true');
  expect(HTMLDialogElement.prototype.showModal).toHaveBeenCalledOnce();
  expect(close).not.toHaveBeenCalled();
});

it('opens from a cell or the keyboard control once, highlights selection, and blocks selection while saving', () => {
  const select = vi.fn();
  const page = { content: [tx], totalElements: 1, totalPages: 1, size: 10, number: 0, first: true, last: true, empty: false };
  const props = { pageData: page, loading: false, onPageChange: vi.fn(), onSelectTransaction: select, selectedTransactionId: 1 };
  const view = render(<TransactionTable {...props} />);
  const row = screen.getAllByRole('row')[1];
  expect(row.getAttribute('aria-selected')).toBe('true');
  fireEvent.click(screen.getAllByRole('cell')[0]); expect(select).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByRole('button', { name: 'Ver detalle de Synthetic sidebar store' })); expect(select).toHaveBeenCalledTimes(2);
  view.rerender(<TransactionTable {...props} selectionDisabled />);
  fireEvent.click(screen.getAllByRole('cell')[0]);
  expect(screen.getByRole('button', { name: 'Ver detalle de Synthetic sidebar store' })).toHaveProperty('disabled', true);
  expect(select).toHaveBeenCalledTimes(2);
});
