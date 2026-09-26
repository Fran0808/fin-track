// @vitest-environment jsdom
import { StrictMode } from 'react';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../src/App';
import { AuthProvider, useAuth } from '../src/contexts/AuthContext';
import { api } from '../src/services/api';

vi.mock('../src/components/effects/LoginWaves', () => ({ LoginWaves: () => null }));

const profile = {
  id: 42, email: 'test@example.test', fullName: 'Test User', pictureUrl: null,
  createdAt: '2026-09-01T12:00:00', lastLoginAt: '2026-09-01T12:00:00',
};

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((complete) => { resolve = complete; });
  return { promise, resolve };
}

function json(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } });
}

beforeEach(() => {
  localStorage.clear();
  window.history.replaceState({}, '', '/');
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe('OAuth session initialization', () => {
  it('waits for the callback token and profile before requesting financial data, including in StrictMode', async () => {
    window.history.replaceState({}, '', '/?auth=google_connected&token=callback-token');
    const userResponse = deferred<void>();
    const requests: Array<{ url: string; authorization: string | null }> = [];
    vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
      requests.push({ url, authorization: new Headers(init?.headers).get('Authorization') });
      if (url.endsWith('/auth/google/me')) {
        await userResponse.promise;
        return json(profile);
      }
      if (url.includes('/analytics/period')) {
        return json({ monthlyExpense: 0, monthlyIncome: 0, channelBreakdown: [], topMerchants: [] });
      }
      if (url.includes('/transactions')) return json({ content: [], totalElements: 0 });
      if (url.endsWith('/auth/google/status')) return json({ connected: false });
      return json({ totalIncome: 0, totalExpense: 0, netBalance: 0, totalTransactions: 0 });
    }));

    render(<StrictMode><AuthProvider><App /></AuthProvider></StrictMode>);

    expect(screen.getByText('Verificando sesión...')).toBeTruthy();
    expect(requests.length).toBeGreaterThan(0);
    expect(requests.every(({ url }) => url.endsWith('/auth/google/me'))).toBe(true);
    expect(requests.every(({ authorization }) => authorization === 'Bearer callback-token')).toBe(true);
    expect(window.location.search).toBe('');

    await act(async () => { userResponse.resolve(); });
    await screen.findByRole('heading', { name: 'Así se movió tu dinero' });
    await waitFor(() => expect(requests.some(({ url }) => url.includes('/transactions'))).toBe(true));
    expect(requests.every(({ authorization }) => authorization === 'Bearer callback-token')).toBe(true);
    expect(localStorage.getItem('auth_token')).toBe('callback-token');

    const clearPolling = vi.spyOn(globalThis, 'clearInterval');
    fireEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }));
    expect(screen.getByRole('heading', { name: 'Entra a tu panel' })).toBeTruthy();
    expect(clearPolling).toHaveBeenCalled();
  });

  it('does not request private data or poll while on the login screen', async () => {
    vi.useFakeTimers();
    const fetch = vi.fn();
    vi.stubGlobal('fetch', fetch);
    render(<AuthProvider><App /></AuthProvider>);
    expect(screen.getByRole('heading', { name: 'Entra a tu panel' })).toBeTruthy();
    await act(async () => { await vi.advanceTimersByTimeAsync(90_000); });
    expect(fetch).not.toHaveBeenCalled();
  });

  it('does not restore the user when a profile response arrives after logout', async () => {
    localStorage.setItem('auth_token', 'old-session');
    const response = deferred<Response>();
    vi.stubGlobal('fetch', vi.fn(() => response.promise));
    function SessionProbe() {
      const { user, logout } = useAuth();
      return <><span>{user?.email ?? 'Signed out'}</span><button onClick={logout}>Logout</button></>;
    }
    render(<AuthProvider><SessionProbe /></AuthProvider>);
    fireEvent.click(screen.getByRole('button', { name: 'Logout' }));
    await act(async () => { response.resolve(json(profile)); });
    expect(screen.getByText('Signed out')).toBeTruthy();
    expect(localStorage.getItem('auth_token')).toBeNull();
  });
});

describe('unauthorized responses', () => {
  it.each([null, 'old-session'])('does not discard a new token after a delayed 401 from %s', async (previousToken) => {
    if (previousToken) localStorage.setItem('auth_token', previousToken);
    const response = deferred<Response>();
    vi.stubGlobal('fetch', vi.fn(() => response.promise));
    const unauthorized = vi.fn();
    window.addEventListener('auth:unauthorized', unauthorized);
    try {
      const request = api.getFinancialSummary();
      const rejection = expect(request).rejects.toThrow('Authentication required');
      localStorage.setItem('auth_token', 'new-session');
      response.resolve(json({ message: 'Authentication required' }, 401));
      await rejection;
      expect(localStorage.getItem('auth_token')).toBe('new-session');
      expect(unauthorized).not.toHaveBeenCalled();
    } finally {
      window.removeEventListener('auth:unauthorized', unauthorized);
    }
  });

  it('still ends the session when its current token is rejected', async () => {
    localStorage.setItem('auth_token', 'expired-session');
    vi.stubGlobal('fetch', vi.fn(async () => json({ message: 'Authentication required' }, 401)));
    const unauthorized = vi.fn();
    window.addEventListener('auth:unauthorized', unauthorized);
    try {
      await expect(api.getCurrentUser()).rejects.toThrow('Authentication required');
      expect(localStorage.getItem('auth_token')).toBeNull();
      expect(unauthorized).toHaveBeenCalledOnce();
    } finally {
      window.removeEventListener('auth:unauthorized', unauthorized);
    }
  });
});
