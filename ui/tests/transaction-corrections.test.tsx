// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { TransactionCorrectionPanel } from '../src/components/transactions/TransactionCorrectionPanel';
import { TransactionDetailModal } from '../src/components/transactions/TransactionDetailModal';
import { api } from '../src/services/api';
import type { Transaction, TransactionCorrectionHistory } from '../src/types';

const original: Transaction = { id: 7, amount: 150, flowType: 'EXPENSE', channel: 'CASH', contactName: 'Synthetic store', transactionDate: '2026-10-01T12:00:00.123456', transactionHash: 'original', createdAt: '2026-10-02T12:00:00' };
const history: TransactionCorrectionHistory = { transaction: original, revision: 0, editable: true, entries: [], totalPages: 0 };
beforeEach(() => {
  vi.spyOn(api,'getTransactionCorrections').mockResolvedValue(history);
  vi.spyOn(api,'correctTransaction').mockResolvedValue({...original,amount:15});
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); });
const panel = () => render(<TransactionCorrectionPanel transactionId={7} mode="edit" onBack={vi.fn()} onSaved={vi.fn()} onBusyChange={vi.fn()} />);

it('requires review and reason, preserves precise unchanged date and writes a decimal string only on confirmation',async()=>{
  const saved=vi.fn();
  render(<TransactionCorrectionPanel transactionId={7} mode="edit" onBack={vi.fn()} onSaved={saved} onBusyChange={vi.fn()} />);
  fireEvent.change(await screen.findByLabelText('Importe (S/)'),{target:{value:'15,00'}});
  fireEvent.change(screen.getByLabelText('Motivo de la corrección'),{target:{value:' Synthetic amount correction '}});
  fireEvent.click(screen.getByRole('button',{name:'Revisar corrección'}));
  expect(screen.getByText('Efecto en los totales')).toBeTruthy(); expect(api.correctTransaction).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button',{name:'Confirmar corrección'}));
  await waitFor(()=>expect(saved).toHaveBeenCalledOnce());
  expect(api.correctTransaction).toHaveBeenCalledWith(7,expect.objectContaining({amount:'15.00',transactionDate:original.transactionDate,expectedRevision:0,flowType:'EXPENSE',reason:'Synthetic amount correction'}));
});
it('shows the financial meaning of a past-date transfer before saving',async()=>{
  panel(); await screen.findByLabelText('Importe (S/)');
  fireEvent.change(screen.getByLabelText('Tipo'),{target:{value:'INTERNAL_TRANSFER'}});
  fireEvent.change(screen.getByLabelText('Fecha y hora del movimiento'),{target:{value:'2026-09-30T10:00'}});
  fireEvent.change(screen.getByLabelText('Motivo de la corrección'),{target:{value:'Synthetic transfer'}});
  fireEvent.click(screen.getByRole('button',{name:'Revisar corrección'}));
  expect(screen.getByText(/Se contabilizan.*transferencia propia/)).toBeTruthy();
  expect(screen.getByText(/transferencias propias se muestran por separado/)).toBeTruthy();
  expect(api.correctTransaction).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button',{name:'Volver y ajustar'}));
  expect((screen.getByLabelText('Tipo') as HTMLSelectElement).value).toBe('INTERNAL_TRANSFER');
});
it('rejects unchanged data and excessive decimal precision',async()=>{
  panel(); await screen.findByLabelText('Importe (S/)');
  fireEvent.change(screen.getByLabelText('Motivo de la corrección'),{target:{value:'Synthetic reason'}});
  fireEvent.click(screen.getByRole('button',{name:'Revisar corrección'})); expect(await screen.findByRole('alert')).toHaveProperty('textContent',expect.stringContaining('Cambia al menos'));
  fireEvent.change(screen.getByLabelText('Importe (S/)'),{target:{value:'1.234'}});
  fireEvent.click(screen.getByRole('button',{name:'Revisar corrección'})); expect(await screen.findByRole('alert')).toHaveProperty('textContent',expect.stringContaining('dos decimales'));
  expect(api.correctTransaction).not.toHaveBeenCalled();
});
it('keeps the same correction intent after an uncertain response',async()=>{
  vi.mocked(api.correctTransaction).mockRejectedValueOnce(new Error('Synthetic interruption'));
  panel(); fireEvent.change(await screen.findByLabelText('Importe (S/)'),{target:{value:'15'}});
  fireEvent.change(screen.getByLabelText('Motivo de la corrección'),{target:{value:'Synthetic reason'}});
  fireEvent.click(screen.getByRole('button',{name:'Revisar corrección'})); fireEvent.click(screen.getByRole('button',{name:'Confirmar corrección'}));
  await screen.findByRole('alert'); const request=vi.mocked(api.correctTransaction).mock.calls[0][1];
  fireEvent.click(screen.getByRole('button',{name:'Confirmar corrección'}));
  await waitFor(()=>expect(api.correctTransaction).toHaveBeenCalledTimes(2));
  expect(vi.mocked(api.correctTransaction).mock.calls[1][1]).toEqual(request);
});
it('requires undoing reconciliation before correction and offers retry after a failed read',async()=>{
  vi.mocked(api.getTransactionCorrections).mockRejectedValueOnce(new Error('Synthetic unavailable')).mockResolvedValue({...history,editable:false});
  panel(); fireEvent.click(await screen.findByRole('button',{name:'Reintentar'}));
  expect(await screen.findByText(/Deshaz la conciliación/)).toBeTruthy();
  expect(screen.queryByLabelText('Importe (S/)')).toBeNull();
});
it('paginates the audit trail and retains before and after evidence',async()=>{
  vi.mocked(api.getTransactionCorrections).mockResolvedValue({...history,totalPages:2,entries:[{id:3,reason:'Synthetic correction',beforeAmount:150,afterAmount:15,beforeDate:'2026-10-01T12:00:00',afterDate:'2026-09-30T10:00:00',beforeFlow:'EXPENSE',afterFlow:'INCOME',beforeOperation:'UNKNOWN',afterOperation:'UNKNOWN',createdAt:'2026-10-29T12:00:00'}]});
  render(<TransactionCorrectionPanel transactionId={7} mode="history" onBack={vi.fn()} onSaved={vi.fn()} onBusyChange={vi.fn()} />);
  expect(await screen.findByText('Synthetic correction')).toBeTruthy(); expect(screen.getByText('Gasto → Entrada')).toBeTruthy();
  fireEvent.click(screen.getByRole('button',{name:'Siguiente'}));
  await waitFor(()=>expect(api.getTransactionCorrections).toHaveBeenCalledWith(7,1));
});
it('opens correction from an accessible pencil icon in the existing detail dialog',async()=>{
  HTMLDialogElement.prototype.showModal=function(){this.open=true;};
  vi.spyOn(api,'getCategories').mockResolvedValue([]); vi.spyOn(api,'getTransactionOrigin').mockResolvedValue({origin:'UNKNOWN',registeredAt:null,operationReference:null});
  render(<TransactionDetailModal transaction={original} onClose={vi.fn()} onUpdated={vi.fn()} />);
  const button=screen.getByRole('button',{name:'Corregir movimiento'});
  expect(button.textContent).toBe('');
  fireEvent.click(button); expect(await screen.findByLabelText('Importe (S/)')).toBeTruthy();
  fireEvent.click(screen.getByRole('button',{name:'Volver al detalle'})); expect(screen.getByRole('heading',{name:'Detalle del movimiento'})).toBeTruthy();
});
