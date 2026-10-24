// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ClassificationRulesView } from '../src/components/views/ClassificationRulesView';
import { api } from '../src/services/api';
import type { Category, ClassificationRule } from '../src/types';

const category: Category = { id: 2, name: 'Ocio', system: false, active: true };
const rule: ClassificationRule = { id: 5, matchText: 'CINE', categoryId: 2, categoryName: 'Ocio', categoryActive: true, priority: 10, enabled: true };

beforeEach(() => {
  vi.spyOn(api, 'getCategories').mockResolvedValue([category]);
  vi.spyOn(api, 'getClassificationRules').mockResolvedValue([rule]);
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); });

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
