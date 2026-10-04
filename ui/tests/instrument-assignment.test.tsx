// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { InstrumentAssignment } from '../src/components/instruments/InstrumentAssignment';
import { api } from '../src/services/api';
import type { FinancialInstrument, Transaction } from '../src/types';

const card: FinancialInstrument = { id: 2, alias: 'Débito diario', bank: 'BCP', type: 'DEBIT_CARD', currency: 'PEN', active: true, lastFour: '1234' };
const movement: Transaction = { id: 5, amount: 25.5, flowType: 'EXPENSE', contactName: 'Comercio', channel: 'TARJETA_DEBITO_BCP', cardLast4: '1234', transactionDate: '2026-10-01T12:00:00', transactionHash: 'fixture', createdAt: '2026-10-01T12:00:00' };
afterEach(() => { cleanup(); vi.restoreAllMocks(); });

it('requires confirmation even with multiple matching suggestions and updates the detail after saving', async () => {
  vi.spyOn(api, 'getFinancialInstruments').mockResolvedValue([card, { ...card, id: 3, alias: 'Otra tarjeta' }]);
  vi.spyOn(api, 'getInstrumentSuggestions').mockResolvedValue([card, { ...card, id: 3, alias: 'Otra tarjeta' }]);
  const assign = vi.spyOn(api, 'assignFinancialInstrument').mockResolvedValue({ ...movement, financialInstrument: card });
  const updated = vi.fn();
  const view = render(<InstrumentAssignment transaction={movement} onUpdated={updated} />);
  await screen.findByRole('button', { name: /Confirmar Débito diario/ });
  expect(assign).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: /Confirmar Débito diario/ }));
  await waitFor(() => expect(updated).toHaveBeenCalledWith(expect.objectContaining({ financialInstrument: card })));
  expect(assign).toHaveBeenCalledWith(5, 2);
  view.rerender(<InstrumentAssignment transaction={{ ...movement, financialInstrument: card }} onUpdated={updated} />);
  await waitFor(() => expect((screen.getByLabelText('Seleccionar producto') as HTMLSelectElement).value).toBe('2'));
});

it('allows manual assignment when no suggestion is available', async () => {
  vi.spyOn(api, 'getFinancialInstruments').mockResolvedValue([card]);
  vi.spyOn(api, 'getInstrumentSuggestions').mockResolvedValue([]);
  const assign = vi.spyOn(api, 'assignFinancialInstrument').mockResolvedValue({ ...movement, financialInstrument: card });
  render(<InstrumentAssignment transaction={movement} onUpdated={vi.fn()} />);
  await screen.findByText('Sin sugerencias');
  fireEvent.change(screen.getByLabelText('Seleccionar producto'), { target: { value: '2' } });
  fireEvent.click(screen.getByRole('button', { name: 'Guardar asignación' }));
  await waitFor(() => expect(assign).toHaveBeenCalledWith(5, 2));
});

it('preserves an archived assignment on screen and allows removing it', async () => {
  vi.spyOn(api, 'getFinancialInstruments').mockResolvedValue([]);
  vi.spyOn(api, 'getInstrumentSuggestions').mockResolvedValue([]);
  const assign = vi.spyOn(api, 'assignFinancialInstrument').mockResolvedValue({ ...movement, financialInstrument: null });
  render(<InstrumentAssignment transaction={{ ...movement, financialInstrument: { ...card, active: false } }} onUpdated={vi.fn()} />);
  await screen.findByText('Sin sugerencias');
  expect(screen.getAllByText('Débito diario (archivado)')).toHaveLength(2);
  fireEvent.change(screen.getByLabelText('Seleccionar producto'), { target: { value: '' } });
  fireEvent.click(screen.getByRole('button', { name: 'Guardar asignación' }));
  await waitFor(() => expect(assign).toHaveBeenCalledWith(5, null));
});

it('shows a failed assignment without reporting success', async () => {
  vi.spyOn(api, 'getFinancialInstruments').mockResolvedValue([card]);
  vi.spyOn(api, 'getInstrumentSuggestions').mockResolvedValue([card]);
  vi.spyOn(api, 'assignFinancialInstrument').mockRejectedValue(new Error('archived'));
  const updated = vi.fn();
  render(<InstrumentAssignment transaction={movement} onUpdated={updated} />);
  fireEvent.click(await screen.findByRole('button', { name: /Confirmar Débito diario/ }));
  expect(await screen.findByRole('alert')).toBeTruthy();
  expect(updated).not.toHaveBeenCalled();
});
