// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ClassificationRulesView } from '../src/components/views/ClassificationRulesView';
import { RuleHistoryModal } from '../src/components/transactions/RuleHistoryModal';
import { api } from '../src/services/api';
import type { ClassificationRule, RuleHistoryPreview } from '../src/types';

const rule: ClassificationRule = { id:7,matchText:'SHOP',categoryId:2,categoryName:'Groceries',categoryActive:true,enabled:true,priority:100 };
const preview: RuleHistoryPreview = { ruleId:7,matchText:'SHOP',categoryName:'Groceries',limit:200,hasMore:false,
  expiresAt:1893456000,previewToken:'a'.repeat(64),transactions:[{id:1,amount:10.25,flowType:'EXPENSE',contactName:'Synthetic SHOP',
    channel:'UNKNOWN',transactionDate:'2026-01-01T12:00:00',createdAt:'2026-01-01T12:00:00',transactionHash:'synthetic-history'}] };

beforeEach(()=>{
  vi.spyOn(api,'getClassificationRules').mockResolvedValue([rule]);
  vi.spyOn(api,'getCategories').mockResolvedValue([{id:2,name:'Groceries',active:true,system:false}]);
  vi.spyOn(api,'previewRuleHistory').mockResolvedValue(preview);
  vi.spyOn(api,'applyRuleHistory').mockResolvedValue({appliedCount:1,skippedCount:0});
  HTMLDialogElement.prototype.showModal = function () { this.open = true; };
  HTMLDialogElement.prototype.close = function () { this.open = false; };
});
afterEach(()=>{cleanup();vi.restoreAllMocks();vi.unstubAllGlobals();});

it('previews from Rules and writes only after explicit confirmation, then refreshes the dashboard',async()=>{
  const refreshed=vi.fn(); render(<ClassificationRulesView onHistoryApplied={refreshed} />);
  fireEvent.click(await screen.findByRole('button',{name:'Aplicar regla SHOP al historial'}));
  expect(await screen.findByText('Synthetic SHOP')).toBeDefined();
  expect(api.applyRuleHistory).not.toHaveBeenCalled();
  expect(within(screen.getByRole('dialog')).getByText(/Todo el historial/)).toBeDefined();
  fireEvent.click(screen.getByRole('button',{name:'Confirmar clasificación (1)'}));
  await waitFor(()=>expect(api.applyRuleHistory).toHaveBeenCalledWith(preview));
  expect(await screen.findByText(/Se clasificaron 1 movimientos/)).toBeDefined();
  expect(refreshed).toHaveBeenCalledOnce(); expect(screen.queryByRole('dialog')).toBeNull();
});

it('cancels the preview without classifying any movement',async()=>{
  const close=vi.fn(); render(<RuleHistoryModal rule={rule} onClose={close} onApplied={vi.fn()} />);
  await screen.findByText('Synthetic SHOP'); fireEvent.click(screen.getByRole('button',{name:'Cancelar'}));
  expect(close).toHaveBeenCalledOnce(); expect(api.applyRuleHistory).not.toHaveBeenCalled();
});

it('returns keyboard focus to the rule action when the dialog closes',async()=>{
  render(<ClassificationRulesView />);
  const launcher=await screen.findByRole('button',{name:'Aplicar regla SHOP al historial'});
  launcher.focus();fireEvent.click(launcher);
  await screen.findByText('Synthetic SHOP');
  fireEvent(screen.getByRole('dialog'),new Event('cancel',{cancelable:true}));
  await waitFor(()=>expect(screen.queryByRole('dialog')).toBeNull());
  expect(document.activeElement).toBe(launcher);expect(api.applyRuleHistory).not.toHaveBeenCalled();
});

it('requires a new preview after a conflict and permits retrying the read',async()=>{
  vi.mocked(api.applyRuleHistory).mockRejectedValue(new Error('La vista previa venció.'));
  render(<RuleHistoryModal rule={rule} onClose={vi.fn()} onApplied={vi.fn()} />);
  fireEvent.click(await screen.findByRole('button',{name:'Confirmar clasificación (1)'}));
  expect((await screen.findByRole('alert')).textContent).toContain('venció');
  expect((screen.getByRole('button',{name:'Confirmar clasificación'}) as HTMLButtonElement).disabled).toBe(true);
  fireEvent.click(screen.getByRole('button',{name:'Revisar de nuevo'}));
  await screen.findByText('Synthetic SHOP'); expect(api.previewRuleHistory).toHaveBeenCalledTimes(2);
});

it('reports no matches, bounded batches and paused rules without enabling unsafe confirmation',async()=>{
  vi.mocked(api.previewRuleHistory).mockResolvedValue({...preview,transactions:[]});
  const view=render(<RuleHistoryModal rule={rule} onClose={vi.fn()} onApplied={vi.fn()} />);
  expect(await screen.findByText(/No hay movimientos anteriores/)).toBeDefined();
  expect((screen.getByRole('button',{name:'Confirmar clasificación (0)'}) as HTMLButtonElement).disabled).toBe(true);
  view.unmount(); vi.mocked(api.previewRuleHistory).mockResolvedValue({...preview,hasMore:true});
  const batch=render(<RuleHistoryModal rule={rule} onClose={vi.fn()} onApplied={vi.fn()} />);
  expect(await screen.findByText(/solo los primeros 200/)).toBeDefined(); batch.unmount();
  vi.mocked(api.getClassificationRules).mockResolvedValue([{...rule,enabled:false}]);
  render(<ClassificationRulesView />);
  expect((await screen.findByRole('button',{name:'Aplicar regla SHOP al historial'}) as HTMLButtonElement).disabled).toBe(true);
});

it('shows preview read errors and closes with Escape without applying',async()=>{
  vi.mocked(api.previewRuleHistory).mockRejectedValue(new Error('No se pudo abrir la vista previa.'));
  const close=vi.fn();render(<RuleHistoryModal rule={rule} onClose={close} onApplied={vi.fn()} />);
  expect(await screen.findByRole('alert')).toBeDefined();
  fireEvent(screen.getByRole('dialog'),new Event('cancel',{cancelable:true}));
  expect(close).toHaveBeenCalledOnce();expect(api.applyRuleHistory).not.toHaveBeenCalled();
});

it('sends only the signed reviewed IDs and rejects incompatible previews',async()=>{
  vi.mocked(api.applyRuleHistory).mockRestore();vi.mocked(api.previewRuleHistory).mockRestore();
  const fetchMock=vi.fn().mockResolvedValue(new Response(JSON.stringify({appliedCount:1,skippedCount:0}),{status:200}));
  vi.stubGlobal('fetch',fetchMock);
  await api.applyRuleHistory(preview);
  expect(fetchMock).toHaveBeenCalledWith('/api/v1/classification-rules/7/history-apply',expect.objectContaining({
    method:'POST',body:JSON.stringify({transactionIds:[1],expiresAt:preview.expiresAt,previewToken:preview.previewToken})}));
  fetchMock.mockImplementation(async()=>new Response('{}',{status:200}));
  await expect(api.applyRuleHistory(preview)).rejects.toThrow('API no confirmó');
  await expect(api.previewRuleHistory(7)).rejects.toThrow('vista previa válida');
  fetchMock.mockResolvedValue(new Response('{}',{status:404}));
  await expect(api.previewRuleHistory(7)).rejects.toThrow('API esté actualizada');
});
