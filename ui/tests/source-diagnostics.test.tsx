// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { SettingsView } from '../src/components/views/SettingsView';
import { PhoneReceptionPanel } from '../src/components/settings/PhoneReceptionPanel';
import { SyncStatus } from '../src/components/layout/SyncStatus';
import { api } from '../src/services/api';
import type { GoogleAuthStatus } from '../src/types';

beforeEach(() => vi.spyOn(api, 'getPhoneReceptionStatus').mockResolvedValue({ lastReceivedAt: null }));
afterEach(() => { cleanup(); vi.restoreAllMocks(); localStorage.clear(); });

const base: GoogleAuthStatus = { connected: true, email: 'owner@example.test', lastSuccessfulSyncAt: '2026-10-26T12:00:00Z', lastSyncFailed: true };
function settings(status = base, unavailable = false, sync = vi.fn()) {
  const refresh = vi.fn().mockResolvedValue(undefined);
  render(<SettingsView googleStatus={status} statusUnavailable={unavailable} syncing={false} onSync={sync} onStatusRefresh={refresh} onOpenPairing={vi.fn()} />);
  return refresh;
}

it('shows the email status and last success separately, hiding stale success when status is unavailable', () => {
  const onSync = vi.fn();
  const view = render(<SyncStatus status={{ ...base, lastSyncFailed: false }} unavailable={false} syncing={false} onSync={onSync} />);
  expect(screen.getByText('Correo sincronizado')).toBeTruthy();
  expect(screen.getByText(/Última correcta:/)).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: 'Sincronizar correos ahora' }));
  expect(onSync).toHaveBeenCalledOnce();
  view.rerender(<SyncStatus status={base} unavailable syncing={false} onSync={onSync} />);
  expect(screen.getByText('Estado de sincronización no disponible')).toBeTruthy();
  expect(screen.queryByText(/Última correcta:/)).toBeNull();
});

it.each([
  ['AUTH_REQUIRED', 'Google requiere una nueva autorización.'],
  ['ACCESS_DENIED', 'Google rechazó el acceso al correo.'],
  ['RATE_LIMITED', 'Google limitó temporalmente las consultas.'],
  ['TEMPORARY', 'No se pudo completar la conexión con Google.'],
  ['PROCESSING', 'Algunos correos no pudieron procesarse.'],
  ['future-code', 'La causa no está disponible.'],
])('offers safe recovery advice for %s and preserves the last success', (code, text) => {
  settings({ ...base, lastSyncFailureCode: code, lastSyncAttemptAt: '2026-10-27T12:00:00Z' });
  expect(screen.getByText(new RegExp(text))).toBeTruthy();
  expect(screen.getByText('Última sincronización correcta')).toBeTruthy();
  expect(screen.getByText('Último intento completado')).toBeTruthy();
  expect(screen.queryByRole('button', { name: 'Volver a conectar Gmail' }) !== null).toBe(['AUTH_REQUIRED', 'ACCESS_DENIED'].includes(code));
});

it('hides stale advice when email status is unavailable but keeps phone reception independent', async () => {
  vi.mocked(api.getPhoneReceptionStatus).mockResolvedValue({ lastReceivedAt: '2026-10-27T12:00:00Z' });
  const refresh = settings({ ...base, lastSyncFailureCode: 'AUTH_REQUIRED' }, true);
  expect(screen.queryByText(/Google requiere una nueva autorización/)).toBeNull();
  expect(await screen.findByRole('button', { name: 'Actualizar estado' })).toBeTruthy();
  expect(screen.queryByText('Sin movimientos recibidos')).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: 'Actualizar estado de correo' }));
  expect(refresh).toHaveBeenCalledOnce();
  expect(screen.queryByText('Android · Yape')).toBeNull();
});

it('reports partial ingestion without claiming synchronization completed', async () => {
  settings({ ...base, lastSyncFailureCode: 'PROCESSING' }, false, vi.fn().mockResolvedValue({ status: 'PARTIAL', savedCount: 2 }));
  fireEvent.click(screen.getByRole('button', { name: 'Sincronizar ahora' }));
  expect(await screen.findByRole('alert')).toHaveProperty('textContent', expect.stringContaining('Sincronización incompleta: 2 movimientos guardados'));
  expect(screen.queryByText(/Sincronización completa:/)).toBeNull();
});

it('distinguishes missing reception from lookup failure and retries independently', async () => {
  vi.mocked(api.getPhoneReceptionStatus).mockRejectedValueOnce(new Error('private internal error'));
  render(<PhoneReceptionPanel />);
  expect(screen.getByText('Consultando recepción...')).toBeTruthy();
  fireEvent.click(await screen.findByRole('button', { name: 'Reintentar recepción' }));
  expect(await screen.findByText('Sin movimientos recibidos')).toBeTruthy();
  expect(screen.queryByText('private internal error')).toBeNull();
  expect(screen.getByText(/Si no llegan movimientos, revisa en el teléfono/).closest('details')).toBeNull();
});

