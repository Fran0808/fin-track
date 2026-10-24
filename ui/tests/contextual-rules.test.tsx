// @vitest-environment jsdom
import { useState } from 'react';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { CreateClassificationRuleModal } from '../src/components/transactions/CreateClassificationRuleModal';
import { TransactionDetailModal } from '../src/components/transactions/TransactionDetailModal';
import { api } from '../src/services/api';
import type { ClassificationRuleDraft, Transaction } from '../src/types';

const transaction: Transaction = { id: 1, contactName: 'Cine Central', amount: 20, flowType: 'EXPENSE', channel: 'YAPE', transactionDate: '2026-10-01T12:00:00', transactionHash: 'synthetic', createdAt: '2026-10-01T12:00:00' };
beforeEach(() => {
  vi.spyOn(api, 'getCategories').mockResolvedValue([{ id: 2, name: 'Ocio', active: true, system: false }, { id: 3, name: 'Retirada', active: false, system: false }]);
  HTMLDialogElement.prototype.showModal = function () { this.open = true; };
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); });

it('prefills a reviewable rule and saves only after explicit submission', async () => {
  const save = vi.spyOn(api, 'saveClassificationRule').mockResolvedValue({ id: 5, matchText: 'CINE', categoryId: 2, categoryName: 'Ocio', categoryActive: true, priority: 10, enabled: true });
  render(<CreateClassificationRuleModal draft={{ matchText: transaction.contactName, categoryName: 'OCIO' }} onClose={vi.fn()} />);
  await waitFor(() => expect((screen.getByLabelText('Asignar categoría') as HTMLSelectElement).value).toBe('2'));
  expect((screen.getByLabelText('El comercio o contacto contiene') as HTMLInputElement).value).toBe(transaction.contactName);
  expect(save).not.toHaveBeenCalled();
  expect(screen.queryByRole('option', { name: 'Retirada' })).toBeNull();
  fireEvent.change(screen.getByLabelText('El comercio o contacto contiene'), { target: { value: ' CINE ' } });
  fireEvent.change(screen.getByLabelText('Prioridad'), { target: { value: '10' } });
  fireEvent.click(screen.getByRole('button', { name: 'Crear regla' }));
  await waitFor(() => expect(save).toHaveBeenCalledWith({ matchText: 'CINE', categoryId: 2, priority: 10, enabled: true }));
  expect(await screen.findByRole('status')).toHaveProperty('textContent', 'Regla creada. Se aplicará a los nuevos movimientos.');
  expect(screen.queryByRole('button', { name: 'Crear regla' })).toBeNull();
});

it('requires an active category and preserves inputs when saving fails', async () => {
  const save = vi.spyOn(api, 'saveClassificationRule').mockRejectedValue(new Error('Error sintético'));
  render(<CreateClassificationRuleModal draft={{ matchText: 'CINE', categoryName: 'Retirada' }} onClose={vi.fn()} />);
  await screen.findByRole('option', { name: 'Ocio' });
  expect((screen.getByRole('button', { name: 'Crear regla' }) as HTMLButtonElement).disabled).toBe(true);
  fireEvent.change(screen.getByLabelText('Asignar categoría'), { target: { value: '2' } });
  fireEvent.click(screen.getByRole('button', { name: 'Crear regla' }));
  expect(await screen.findByRole('alert')).toHaveProperty('textContent', 'Error sintético');
  expect((screen.getByLabelText('El comercio o contacto contiene') as HTMLInputElement).value).toBe('CINE');
  expect(save).toHaveBeenCalledTimes(1);
});

it('opens a sibling modal with the edited category and retains the movement draft on cancellation', async () => {
  const save = vi.spyOn(api, 'saveClassificationRule');
  function Detail() {
    const [draft, setDraft] = useState<ClassificationRuleDraft | null>(null);
    return <><TransactionDetailModal transaction={transaction} onClose={vi.fn()} onCreateRule={setDraft} />
      {draft && <CreateClassificationRuleModal draft={draft} onClose={() => setDraft(null)} />}</>;
  }
  render(<Detail />);
  await screen.findByRole('option', { name: 'Ocio' });
  fireEvent.change(screen.getByLabelText('Categoría'), { target: { value: 'Ocio' } });
  fireEvent.change(screen.getByLabelText('Notas personales'), { target: { value: 'Borrador pendiente' } });
  fireEvent.click(screen.getByRole('button', { name: 'Crear regla para este comercio' }));
  const dialog = screen.getByRole('dialog', { name: 'Crear regla para este comercio' });
  await waitFor(() => expect((within(dialog).getByLabelText('Asignar categoría') as HTMLSelectElement).value).toBe('2'));
  expect(dialog.parentElement?.closest('dialog')).toBeNull();
  fireEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }));
  expect(screen.queryByRole('dialog', { name: 'Crear regla para este comercio' })).toBeNull();
  expect((screen.getByLabelText('Notas personales') as HTMLTextAreaElement).value).toBe('Borrador pendiente');
  expect((screen.getByLabelText('Categoría') as HTMLSelectElement).value).toBe('Ocio');
  expect(save).not.toHaveBeenCalled();
});

it('does not offer categorization rules for internal transfers', async () => {
  render(<TransactionDetailModal transaction={{ ...transaction, flowType: 'INTERNAL_TRANSFER' }} onClose={vi.fn()} onCreateRule={vi.fn()} />);
  await screen.findByRole('option', { name: 'Ocio' });
  expect(screen.queryByRole('button', { name: 'Crear regla para este comercio' })).toBeNull();
});

it('blocks saving when the catalog fails and allows retry without losing the draft', async () => {
  vi.mocked(api.getCategories).mockRejectedValueOnce(new Error('Catálogo no disponible'));
  const save = vi.spyOn(api, 'saveClassificationRule');
  render(<CreateClassificationRuleModal draft={{ matchText: 'CINE', categoryName: 'Ocio' }} onClose={vi.fn()} />);
  await screen.findByRole('alert');
  expect((screen.getByRole('button', { name: 'Crear regla' }) as HTMLButtonElement).disabled).toBe(true);
  fireEvent.click(screen.getByRole('button', { name: 'Reintentar categorías' }));
  await waitFor(() => expect((screen.getByLabelText('Asignar categoría') as HTMLSelectElement).value).toBe('2'));
  expect((screen.getByLabelText('El comercio o contacto contiene') as HTMLInputElement).value).toBe('CINE');
  expect(save).not.toHaveBeenCalled();
});
