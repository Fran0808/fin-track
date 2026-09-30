// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import type { ComponentProps } from 'react';
import { afterEach, expect, it, vi } from 'vitest';
import { SettingsView } from '../src/components/views/SettingsView';
import { api } from '../src/services/api';
import type { EmailSyncResponse, GoogleAuthStatus } from '../src/types';

const connectedStatus: GoogleAuthStatus = {
  connected: true,
  email: 'finanzas@example.test',
  lastSuccessfulSyncAt: '2026-09-29T20:15:00Z',
  lastSyncFailed: false,
};

const syncResult: EmailSyncResponse = {
  status: 'SUCCESS',
  scannedCount: 5,
  processedInBatch: 2,
  savedCount: 2,
  transactions: [],
  message: 'Completed',
};

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

function renderSettings(overrides: Partial<ComponentProps<typeof SettingsView>> = {}) {
  const props: ComponentProps<typeof SettingsView> = {
    googleStatus: connectedStatus,
    statusUnavailable: false,
    syncing: false,
    onSync: vi.fn().mockResolvedValue(syncResult),
    onStatusRefresh: vi.fn().mockResolvedValue(undefined),
    onOpenPairing: vi.fn(),
    ...overrides,
  };
  render(<SettingsView {...props} />);
  return props;
}

it('shows the real Gmail state and opens mobile pairing', () => {
  const props = renderSettings();
  expect(screen.getByText('finanzas@example.test')).toBeTruthy();
  expect(screen.getByText('Conectado')).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: 'Abrir vinculación móvil' }));
  expect(props.onOpenPairing).toHaveBeenCalledOnce();
});

it('reports how many new movements were saved after a manual sync', async () => {
  const props = renderSettings();
  fireEvent.click(screen.getByRole('button', { name: 'Sincronizar ahora' }));
  expect(await screen.findByText('Sincronización completa: 2 movimientos nuevos.')).toBeTruthy();
  expect(props.onSync).toHaveBeenCalledOnce();
});

it('disconnects Gmail only after confirmation and refreshes its status', async () => {
  vi.spyOn(window, 'confirm').mockReturnValue(true);
  const disconnect = vi.spyOn(api, 'disconnectGoogle').mockResolvedValue(undefined);
  const props = renderSettings();
  fireEvent.click(screen.getByRole('button', { name: 'Desvincular Gmail' }));
  expect(await screen.findByText('Gmail fue desvinculado.')).toBeTruthy();
  expect(disconnect).toHaveBeenCalledOnce();
  expect(props.onStatusRefresh).toHaveBeenCalledOnce();
});
