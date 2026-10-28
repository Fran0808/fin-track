// @vitest-environment jsdom
import { afterEach, expect, it, vi } from 'vitest';
import { api } from '../src/services/api';
afterEach(() => { vi.restoreAllMocks(); localStorage.clear(); });

it('validates phone responses from older APIs and sends web authentication without caching', async () => {
  localStorage.setItem('auth_token', 'synthetic-web-token');
  const fetcher = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('{}'));
  await expect(api.getPhoneReceptionStatus()).rejects.toThrow('información válida');
  expect(fetcher.mock.calls[0][0]).toBe('/api/v1/user/phone-status');
  expect(fetcher.mock.calls[0][1]?.cache).toBe('no-store');
  expect(new Headers(fetcher.mock.calls[0][1]?.headers).get('Authorization')).toBe('Bearer synthetic-web-token');
  fetcher.mockResolvedValue(new Response('{"lastReceivedAt":"invalid"}'));
  await expect(api.getPhoneReceptionStatus()).rejects.toThrow('información válida');
  fetcher.mockResolvedValue(new Response('{"lastReceivedAt":null}'));
  expect(await api.getPhoneReceptionStatus()).toEqual({ lastReceivedAt: null });
});
