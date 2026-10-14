// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { PendingView } from '../src/components/views/PendingView';
import { ClassificationRulesView } from '../src/components/views/ClassificationRulesView';
import { api } from '../src/services/api';
import type { Category, ClassificationRule, PageResponse, Transaction } from '../src/types';

const transaction: Transaction = { id: 1, contactName: 'Cine Central', amount: 20, flowType: 'EXPENSE', channel: 'YAPE', transactionDate: '2026-10-01T12:00:00', transactionHash: 'synthetic', createdAt: '2026-10-01T12:00:00' };
const emptyPage: PageResponse<Transaction> = { content: [], totalElements: 0, totalPages: 0, size: 10, number: 0, first: true, last: true, empty: true };
const pendingPage = { ...emptyPage, content: [transaction], totalElements: 1, totalPages: 1, empty: false };
const category: Category = { id: 2, name: 'Ocio', system: false, active: true };
const rule: ClassificationRule = { id: 5, matchText: 'CINE', categoryId: 2, categoryName: 'Ocio', categoryActive: true, priority: 10, enabled: true };

beforeEach(() => {
  vi.spyOn(api, 'getCategories').mockResolvedValue([category]);
  vi.spyOn(api, 'getClassificationRules').mockResolvedValue([rule]);
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); });

it('loads pending records, opens details and removes resolved records after revision changes', async () => {
  const pending = vi.spyOn(api, 'getPendingTransactions').mockResolvedValueOnce(pendingPage).mockResolvedValue(emptyPage);
  const select = vi.fn(); const rules = vi.fn();
  const view = render(<PendingView revision={0} onSelectTransaction={select} onOpenRules={rules} />);
  fireEvent.click(await screen.findByRole('button', { name: 'Completar Cine Central' }));
  expect(select).toHaveBeenCalledWith(transaction);
  expect(screen.getByText('1 movimiento por completar')).toBeDefined();
  fireEvent.click(screen.getByRole('button', { name: 'Configurar reglas' })); expect(rules).toHaveBeenCalled();
  view.rerender(<PendingView revision={1} onSelectTransaction={select} onOpenRules={rules} />);
  expect(await screen.findByText('Todo al día')).toBeDefined();
  expect(pending).toHaveBeenCalledTimes(2);
});

it('filters pending reasons, paginates and resets to the first page on filter changes', async () => {
  const pending = vi.spyOn(api, 'getPendingTransactions').mockResolvedValue({ ...pendingPage, totalElements: 12, totalPages: 2, last: false });
  render(<PendingView revision={0} onSelectTransaction={vi.fn()} onOpenRules={vi.fn()} />);
  fireEvent.click(await screen.findByRole('button', { name: 'Pendientes siguientes' }));
  await waitFor(() => expect(pending).toHaveBeenCalledWith('ALL', 1));
  fireEvent.click(screen.getByRole('button', { name: 'Sin categoría' }));
  await waitFor(() => expect(pending).toHaveBeenCalledWith('CATEGORY', 0));
  fireEvent.click(screen.getByRole('button', { name: 'Sin medio asignado' }));
  await waitFor(() => expect(pending).toHaveBeenCalledWith('INSTRUMENT', 0));
});

it('shows pending load failures with retry and never claims the inbox is empty on error', async () => {
  vi.spyOn(api, 'getPendingTransactions').mockRejectedValueOnce(new Error('offline')).mockResolvedValue(emptyPage);
  render(<PendingView revision={0} onSelectTransaction={vi.fn()} onOpenRules={vi.fn()} />);
  expect(await screen.findByRole('alert')).toBeDefined();
  expect(screen.queryByText('Todo al día')).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
  expect(await screen.findByText('Todo al día')).toBeDefined();
});

it('does not replace the new pending filter with a delayed response from the old filter', async () => {
  let completeOld!: (page: PageResponse<Transaction>) => void;
  vi.spyOn(api, 'getPendingTransactions').mockReturnValueOnce(new Promise(resolve => { completeOld = resolve; })).mockResolvedValue(emptyPage);
  render(<PendingView revision={0} onSelectTransaction={vi.fn()} onOpenRules={vi.fn()} />);
  fireEvent.click(screen.getByRole('button', { name: 'Sin categoría' }));
  await screen.findByText('Todo al día');
  completeOld(pendingPage);
  await waitFor(() => expect(screen.queryByText('Cine Central')).toBeNull());
});

it('creates a trimmed rule with a catalog category and explicit priority', async () => {
  const save = vi.spyOn(api, 'saveClassificationRule').mockResolvedValue(rule);
  render(<ClassificationRulesView />);
  await screen.findByRole('button', { name: 'Editar regla CINE' });
  fireEvent.change(screen.getByLabelText('El comercio o contacto contiene'), { target: { value: '  CINE  ' } });
  fireEvent.change(screen.getByLabelText('Asignar categoría'), { target: { value: '2' } });
  fireEvent.change(screen.getByLabelText('Prioridad'), { target: { value: '10' } });
  fireEvent.click(screen.getByRole('button', { name: 'Crear regla' }));
  await waitFor(() => expect(save).toHaveBeenCalledWith({ matchText: 'CINE', categoryId: 2, priority: 10, enabled: true }, undefined));
  expect(await screen.findByText('Regla creada. Se aplicará a los nuevos movimientos.')).toBeDefined();
});

it('edits and pauses a rule without changing its target', async () => {
  const save = vi.spyOn(api, 'saveClassificationRule').mockResolvedValue(rule);
  render(<ClassificationRulesView />);
  fireEvent.click(await screen.findByRole('button', { name: 'Editar regla CINE' }));
  fireEvent.change(screen.getByLabelText('Prioridad'), { target: { value: '1' } });
  fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));
  await waitFor(() => expect(save).toHaveBeenCalledWith({ matchText: 'CINE', categoryId: 2, priority: 1, enabled: true }, 5));
  fireEvent.click(await screen.findByRole('button', { name: 'Pausar regla CINE' }));
  await waitFor(() => expect(save).toHaveBeenCalledWith({ matchText: 'CINE', categoryId: 2, priority: 10, enabled: false }, 5));
});

it('confirms rule deletion and preserves the form when a save fails', async () => {
  const remove = vi.spyOn(api, 'deleteClassificationRule').mockResolvedValue();
  vi.spyOn(api, 'saveClassificationRule').mockRejectedValue(new Error('Selecciona una categoría activa.'));
  render(<ClassificationRulesView />);
  fireEvent.click(await screen.findByRole('button', { name: 'Editar regla CINE' }));
  fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));
  expect((await screen.findByRole('alert')).textContent).toContain('Selecciona una categoría activa');
  expect((screen.getByLabelText('El comercio o contacto contiene') as HTMLInputElement).value).toBe('CINE');
  fireEvent.click(screen.getByRole('button', { name: 'Eliminar regla CINE' }));
  expect(remove).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar eliminación' }));
  await waitFor(() => expect(remove).toHaveBeenCalledWith(5));
});

it('prevents reactivating a rule with an inactive category and reports catalog failures', async () => {
  vi.mocked(api.getClassificationRules).mockResolvedValue([{ ...rule, enabled: false, categoryActive: false }]);
  const view = render(<ClassificationRulesView />);
  expect((await screen.findByRole('button', { name: 'Activar regla CINE' }) as HTMLButtonElement).disabled).toBe(true);
  view.unmount();
  vi.mocked(api.getCategories).mockRejectedValue(new Error('offline'));
  render(<ClassificationRulesView />);
  expect(await screen.findByRole('alert')).toBeDefined();
  expect(screen.getByRole('button', { name: 'Reintentar' })).toBeDefined();
});
