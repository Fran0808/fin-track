// @vitest-environment jsdom
import { afterEach, expect, it, vi } from 'vitest';
import { api } from '../src/services/api';
import type { ManualTransactionRequest, Transaction } from '../src/types';

const request: ManualTransactionRequest = { requestId: '01234567-89ab-cdef-0123-456789abcdef', amount: '0.10', flowType: 'EXPENSE',
  contactName: 'Synthetic', transactionDate: '2026-01-31T12:00:00', paymentMethod: 'CASH', categoryId: null, financialInstrumentId: null };
const manual: Transaction = { id: 1, captureOrigin: 'MANUAL', amount: 0.1, flowType: 'EXPENSE', contactName: 'Synthetic',
  channel: 'CASH', transactionDate: request.transactionDate, transactionHash: 'synthetic', createdAt: request.transactionDate };
afterEach(() => { vi.restoreAllMocks(); localStorage.clear(); });

it('sends a web-authenticated decimal intent and rejects unverified creation responses', async () => {
  localStorage.setItem('auth_token', 'synthetic-web-token');
  const fetcher = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(new Response(JSON.stringify(manual)));
  expect(await api.createManualTransaction(request)).toEqual(manual);
  expect(fetcher.mock.calls[0][0]).toBe('/api/v1/transactions/manual');
  expect(new Headers(fetcher.mock.calls[0][1]?.headers).get('Authorization')).toBe('Bearer synthetic-web-token');
  expect(JSON.parse(String(fetcher.mock.calls[0][1]?.body)).amount).toBe('0.10');
  fetcher.mockResolvedValueOnce(new Response('{}'));
  await expect(api.createManualTransaction(request)).rejects.toThrow('Reintenta con los mismos datos');
});

it('loads private review data without caching and rejects malformed or unsigned candidates', async () => {
  const fetcher = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(new Response('{}'));
  await expect(api.previewManualReconciliation(1)).rejects.toThrow('revisión válida');
  expect(fetcher.mock.calls[0][1]?.cache).toBe('no-store');
  fetcher.mockResolvedValueOnce(new Response(JSON.stringify({ manual, candidates: [{ transaction: { ...manual, id: 2, captureOrigin: 'EMAIL' }, previewToken: 'bad' }], expiresAt: 1893456000 })));
  await expect(api.previewManualReconciliation(1)).rejects.toThrow('revisión válida');
});

it('binds confirmation to the reviewed target and rejects a response for another movement', async () => {
  const fetcher = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(new Response(JSON.stringify({ ...manual, captureOrigin: 'EMAIL' })));
  await expect(api.reconcileManual(1, 2, 1893456000, 'a'.repeat(64))).rejects.toThrow('verificar la conciliación');
  expect(JSON.parse(String(fetcher.mock.calls[0][1]?.body))).toEqual({ capturedId: 2, expiresAt: 1893456000, previewToken: 'a'.repeat(64) });
});
