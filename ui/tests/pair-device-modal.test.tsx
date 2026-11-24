// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { PairDeviceModal } from '../src/components/auth/PairDeviceModal';
import { api } from '../src/services';
import type { DevicePairingInfo } from '../src/types';

const pairing: DevicePairingInfo = {
  userId: 1,
  userEmail: 'demo@example.test',
  pairingToken: 'synthetic-device-token',
  serverUrl: 'https://fintrack.example.test',
  qrPayload: '',
};

beforeEach(() => {
  vi.stubEnv('VITE_ANDROID_APK_URL', '');
  HTMLDialogElement.prototype.showModal = function () {
    this.open = true;
  };
  vi.spyOn(api, 'getPairingInfo').mockResolvedValue(pairing);
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllEnvs();
});

function showPairing() {
  return render(<PairDeviceModal isOpen onClose={vi.fn()} />);
}

it('shows a configured Android download and starts with manual setup collapsed', async () => {
  const apkUrl = 'https://downloads.example.test/fintrack.apk';
  vi.stubEnv('VITE_ANDROID_APK_URL', apkUrl);
  showPairing();

  expect(screen.getByRole('link', { name: 'Descargar para Android' })).toHaveProperty('href', apkUrl);
  await screen.findByText('Escanear QR');
  const disclosure = await screen.findByText('¿No puedes escanear el QR?');
  expect(disclosure.closest('details')).toHaveProperty('open', false);
});

it.each(['', 'http://downloads.example.test/fintrack.apk', 'javascript:alert(1)'])(
  'allows pairing without a download when the configured URL is %j',
  async apkUrl => {
    vi.stubEnv('VITE_ANDROID_APK_URL', apkUrl);
    showPairing();

    expect(screen.queryByRole('link', { name: 'Descargar para Android' })).toBeNull();
    expect(screen.getByText(/La descarga aún no está disponible aquí/)).toBeDefined();
    expect(await screen.findByText('Código QR para vincular FinTrack')).toBeDefined();
  },
);

it('copies the edited server address and current code from manual setup', async () => {
  const writeText = vi.fn().mockResolvedValue(undefined);
  const clipboard = Object.getOwnPropertyDescriptor(navigator, 'clipboard');
  Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } });

  try {
    showPairing();
    fireEvent.click(await screen.findByText('¿No puedes escanear el QR?'));

    const qr = screen.getByText('Código QR para vincular FinTrack').closest('svg');
    const originalPath = qr?.lastElementChild?.getAttribute('d');
    const editedUrl = 'https://other.example.test';
    fireEvent.change(screen.getByLabelText('Dirección del servidor'), { target: { value: editedUrl } });
    expect(qr?.lastElementChild?.getAttribute('d')).not.toBe(originalPath);

    fireEvent.click(screen.getByRole('button', { name: 'Copiar dirección del servidor' }));
    await waitFor(() => expect(writeText).toHaveBeenCalledWith(editedUrl));
    fireEvent.click(screen.getByRole('button', { name: 'Copiar código de vinculación' }));
    await waitFor(() => expect(writeText).toHaveBeenCalledWith(pairing.pairingToken));
    expect(await screen.findByText('Código copiado.')).toBeDefined();
  } finally {
    if (clipboard) Object.defineProperty(navigator, 'clipboard', clipboard);
    else Reflect.deleteProperty(navigator, 'clipboard');
  }
});

it('retries a failed QR request without hiding the installer', async () => {
  vi.stubEnv('VITE_ANDROID_APK_URL', 'https://downloads.example.test/fintrack.apk');
  vi.mocked(api.getPairingInfo).mockRejectedValueOnce(new Error('Synthetic failure'));
  showPairing();

  expect(await screen.findByRole('alert')).toHaveProperty('textContent', 'No pudimos cargar tu código de vinculación.');
  expect(screen.getByRole('link', { name: 'Descargar para Android' })).toBeDefined();
  fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
  expect(await screen.findByText('Código QR para vincular FinTrack')).toBeDefined();
});

it('requires confirmation before replacing the pairing code', async () => {
  const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);
  const replace = vi.spyOn(api, 'regeneratePairingToken').mockResolvedValue({
    ...pairing,
    pairingToken: 'new-synthetic-device-token',
  });
  showPairing();
  fireEvent.click(await screen.findByText('¿No puedes escanear el QR?'));

  const regenerate = screen.getByRole('button', { name: 'Generar un código nuevo' });
  fireEvent.click(regenerate);
  expect(confirm).toHaveBeenCalledOnce();
  expect(replace).not.toHaveBeenCalled();

  fireEvent.click(regenerate);
  expect(await screen.findByText('Nuevo código listo para vincular.')).toBeDefined();
  expect(replace).toHaveBeenCalledOnce();
  expect(screen.getByLabelText('Código de vinculación')).toHaveProperty('value', 'new-synthetic-device-token');
});
