// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { TransactionOriginPanel } from '../src/components/transactions/TransactionOriginPanel';
import { api } from '../src/services/api';
import type { TransactionOrigin } from '../src/types';
import { formatDate } from '../src/utils';

const email: TransactionOrigin = { origin:'EMAIL',registeredAt:'2026-01-03T12:34:56',operationReference:'OP-123' };
it('distinguishes manual records from automatic captures without inventing a receipt',async()=>{
  vi.spyOn(api,'getTransactionOrigin').mockResolvedValue({...email,origin:'MANUAL',operationReference:null});
  render(<TransactionOriginPanel transactionId={7} />);
  expect(await screen.findByText('Manual')).toBeDefined();
  expect(screen.getByText(formatDate(email.registeredAt!).replace(/\s+/g,' '))).toBeDefined();
  expect(screen.queryByText('Correo')).toBeNull();
  expect(screen.queryByText('Referencia')).toBeNull();
});
afterEach(()=>{cleanup();vi.restoreAllMocks();vi.unstubAllGlobals();});

it('keeps the registration date visible when the delayed email reference arrives', async () => {
  let resolve!: (value: TransactionOrigin) => void;
  const origin = new Promise<TransactionOrigin>(complete => { resolve = complete; });
  const load = vi.spyOn(api, 'getTransactionOrigin').mockReturnValue(origin);
  render(<TransactionOriginPanel transactionId={7} captureOrigin="EMAIL" createdAt={email.registeredAt!} />);

  const label = screen.getByText('Registro');
  const date = screen.getByText(formatDate(email.registeredAt!).replace(/\s+/g, ' '));
  expect(screen.getByText('Correo')).toBeDefined();
  await act(async () => resolve(email));

  expect(screen.getByText('Registro')).toBe(label);
  expect(screen.getByText(formatDate(email.registeredAt!).replace(/\s+/g, ' '))).toBe(date);
  expect(screen.queryByText('Referencia')).toBeNull();
  expect(screen.queryByText(email.operationReference!)).toBeNull();
  expect(load).toHaveBeenCalledWith(7);
});

it('labels phone captures as Teléfono and never displays phone security text as a receipt',async()=>{
  vi.spyOn(api,'getTransactionOrigin').mockResolvedValue({...email,origin:'PHONE',operationReference:'SHOULD-NOT-BE-DISPLAYED'});
  render(<TransactionOriginPanel transactionId={7} />);
  expect(await screen.findByText('Teléfono')).toBeDefined();expect(screen.queryByText(/Android/)).toBeNull();
  expect(screen.queryByText('SHOULD-NOT-BE-DISPLAYED')).toBeNull();
  expect(screen.getByText(formatDate(email.registeredAt!).replace(/\s+/g,' '))).toBeDefined();
});

it('does not invent the origin of historical captures while retaining their registration time',async()=>{
  vi.spyOn(api,'getTransactionOrigin').mockResolvedValue({...email,origin:'UNKNOWN',operationReference:null});
  render(<TransactionOriginPanel transactionId={7} />);
  expect(await screen.findByText('Origen no disponible')).toBeDefined();
  expect(screen.queryByText('Correo')).toBeNull();expect(screen.queryByText('Teléfono')).toBeNull();
  expect(screen.getByText(formatDate(email.registeredAt!).replace(/\s+/g,' '))).toBeDefined();
});

it('offers retry after an unavailable source without reporting an invented origin',async()=>{
  vi.spyOn(api,'getTransactionOrigin').mockRejectedValueOnce(new Error('Unavailable')).mockResolvedValue({...email,origin:'PHONE',operationReference:null});
  render(<TransactionOriginPanel transactionId={7} />);
  fireEvent.click(await screen.findByRole('button',{name:'Reintentar origen'}));
  expect(await screen.findByText('Teléfono')).toBeDefined();
  expect(api.getTransactionOrigin).toHaveBeenCalledTimes(2);
});

it('does not publish a previous movement’s delayed origin after switching movements',async()=>{
  let resolve!: (value:TransactionOrigin)=>void;
  const old=new Promise<TransactionOrigin>(accept=>{resolve=accept;});
  const phoneDate = '2026-01-04T12:34:56';
  vi.spyOn(api,'getTransactionOrigin').mockImplementation(id=>id===1?old:Promise.resolve({...email,origin:'PHONE',registeredAt:phoneDate,operationReference:null}));
  const {rerender}=render(<TransactionOriginPanel transactionId={1} />);
  expect(screen.getByText('Consultando origen...')).toBeDefined();
  rerender(<TransactionOriginPanel transactionId={2} />);
  await screen.findByText('Teléfono');
  await act(async()=>resolve(email));
  expect(screen.getByText(formatDate(phoneDate).replace(/\s+/g, ' '))).toBeDefined();
  expect(screen.queryByText(formatDate(email.registeredAt!).replace(/\s+/g, ' '))).toBeNull();
  expect(screen.queryByText('Correo')).toBeNull();
});

it('uses authenticated no-store reads and handles older or malformed API responses',async()=>{
  localStorage.setItem('auth_token','synthetic-origin-token');
  const fetchMock=vi.fn().mockImplementation(async()=>new Response(JSON.stringify(email),{status:200}));
  vi.stubGlobal('fetch',fetchMock);
  await expect(api.getTransactionOrigin(7)).resolves.toEqual(email);
  expect(fetchMock).toHaveBeenCalledWith('/api/v1/transactions/7/origin',expect.objectContaining({cache:'no-store'}));
  const headers=fetchMock.mock.calls[0][1].headers as Headers;
  expect(headers.get('Authorization')).toBe('Bearer synthetic-origin-token');
  fetchMock.mockImplementation(async()=>new Response('{}',{status:200}));
  await expect(api.getTransactionOrigin(7)).rejects.toThrow('información válida');
  fetchMock.mockImplementation(async()=>new Response('{}',{status:404}));
  await expect(api.getTransactionOrigin(7)).rejects.toThrow('404');
  localStorage.removeItem('auth_token');
});
