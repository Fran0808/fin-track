// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ManualReconciliationPanel } from '../src/components/transactions/ManualReconciliationPanel';
import { api } from '../src/services/api';
import type { Transaction, ManualReconciliationPreview } from '../src/types';

const manual: Transaction = { id: 1, captureOrigin: 'MANUAL', amount: 12.34, flowType: 'EXPENSE',
  contactName: 'Synthetic manual', channel: 'CASH', transactionDate: '2026-01-31T12:00:00', transactionHash: 'manual:synthetic', createdAt: '2026-02-01T12:00:00' };
const captured: Transaction = { ...manual, id: 2, captureOrigin: 'EMAIL', contactName: 'Synthetic receipt', channel: 'WALLET', transactionDate: '2026-02-01T12:00:00', transactionHash: 'synthetic-capture' };
const preview: ManualReconciliationPreview = { manual, candidates: [{ transaction: captured, previewToken: 'a'.repeat(64) }], limit: 50, hasMore: false, expiresAt: 1893456000 };

beforeEach(() => {
  vi.spyOn(api, 'getManualReconciliation').mockResolvedValue({ manual, captured: null, reconciledAt: null, revision: 0 });
  vi.spyOn(api, 'previewManualReconciliation').mockResolvedValue(preview);
  vi.spyOn(api, 'reconcileManual').mockResolvedValue(captured);
  vi.spyOn(api, 'undoManualReconciliation').mockResolvedValue(captured);
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); });

it('requires selecting and reviewing a signed capture before linking the manual entry', async () => {
  const updated = vi.fn(); render(<ManualReconciliationPanel transaction={manual} onUpdated={updated} onBusyChange={vi.fn()} />);
  fireEvent.click(await screen.findByRole('button', { name: 'Buscar capturas para conciliar' }));
  await screen.findByText('Synthetic receipt');
  expect(screen.getByRole('button', { name: 'Confirmar conciliación' })).toHaveProperty('disabled', true);
  expect(api.reconcileManual).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('radio'));
  expect(screen.getByText(/meses distintos/)).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar conciliación' }));
  await waitFor(() => expect(updated).toHaveBeenCalledWith(captured));
  expect(api.reconcileManual).toHaveBeenCalledWith(1, 2, preview.expiresAt, 'a'.repeat(64));
});

it('cancels candidate review without changing totals and shows empty and limited results honestly', async () => {
  vi.mocked(api.previewManualReconciliation).mockResolvedValue({ ...preview, candidates: [], hasMore: true });
  render(<ManualReconciliationPanel transaction={manual} onUpdated={vi.fn()} onBusyChange={vi.fn()} />);
  fireEvent.click(await screen.findByRole('button', { name: 'Buscar capturas para conciliar' }));
  expect(await screen.findByText(/No hay capturas compatibles/)).toBeTruthy();
  expect(screen.getByText(/Hay más capturas compatibles/)).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: 'Cancelar revisión' }));
  expect(api.reconcileManual).not.toHaveBeenCalled();
});

it('keeps archived manual evidence visible and confirms the double-counting effect before undo', async () => {
  vi.mocked(api.getManualReconciliation).mockResolvedValue({ manual: { ...manual, reconciledToId: 2 }, captured, reconciledAt: '2026-02-01T13:00:00', revision: 1 });
  const updated = vi.fn(); render(<ManualReconciliationPanel transaction={captured} onUpdated={updated} onBusyChange={vi.fn()} />);
  fireEvent.click(await screen.findByRole('button', { name: 'Deshacer conciliación' }));
  expect(screen.getByText(/volverás a contarla dos veces/)).toBeTruthy();
  expect(api.undoManualReconciliation).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar deshacer conciliación' }));
  await waitFor(() => expect(updated).toHaveBeenCalledWith(captured));
  expect(api.undoManualReconciliation).toHaveBeenCalledWith(1, 2, 1);
});

it('preserves the review and offers refresh after a stale confirmation fails', async () => {
  vi.mocked(api.reconcileManual).mockRejectedValueOnce(new Error('La revisión venció.'));
  render(<ManualReconciliationPanel transaction={manual} onUpdated={vi.fn()} onBusyChange={vi.fn()} />);
  fireEvent.click(await screen.findByRole('button', { name: 'Buscar capturas para conciliar' }));
  fireEvent.click(await screen.findByRole('radio')); fireEvent.click(screen.getByRole('button', { name: 'Confirmar conciliación' }));
  expect(await screen.findByRole('alert')).toHaveProperty('textContent', 'La revisión venció.');
  fireEvent.click(screen.getByRole('button', { name: 'Actualizar coincidencias' }));
  await waitFor(() => expect(api.previewManualReconciliation).toHaveBeenCalledTimes(2));
});

it('updates archived state without replacing the selected capture after loading linked evidence', async () => {
  vi.mocked(api.getManualReconciliation).mockResolvedValue({ manual: { ...manual, reconciledToId: 2 }, captured, reconciledAt: '2026-02-01T13:00:00', revision: 1 });
  const updated = vi.fn(), archived = vi.fn();
  render(<ManualReconciliationPanel transaction={manual} onUpdated={updated} onBusyChange={vi.fn()} onArchivedChange={archived} />);
  await waitFor(() => expect(archived).toHaveBeenCalledWith(true));
  expect(updated).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: 'Ver captura conservada' }));
  expect(updated).toHaveBeenCalledWith(captured);
});
