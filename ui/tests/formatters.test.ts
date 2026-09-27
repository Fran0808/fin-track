import { afterEach, describe, expect, it, vi } from 'vitest';
import { formatDate, formatRelativeDate } from '../src/utils/formatters';

afterEach(() => vi.useRealTimers());

describe('formatRelativeDate', () => {
  it('distinguishes yesterday from today even within the last 24 hours', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 8, 27, 13));

    expect(formatRelativeDate('2026-09-27T12:58:00')).toBe('Hoy');
    expect(formatRelativeDate('2026-09-26T19:00:00')).toBe('Ayer');
  });

  it('uses calendar boundaries across midnight and a year change', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2027, 0, 1, 0, 1));

    expect(formatRelativeDate('2026-12-31T23:59:00')).toBe('Ayer');
    expect(formatRelativeDate('2026-12-30T23:59:00')).toBe('Hace 2 días');
    expect(formatRelativeDate('2026-12-25T23:59:00')).toBe(formatDate('2026-12-25T23:59:00'));
  });

  it('does not render negative day counts or invalid dates as relative labels', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 8, 27, 13));

    expect(formatRelativeDate('2026-09-28T09:00:00')).toBe(formatDate('2026-09-28T09:00:00'));
    expect(formatRelativeDate('invalid')).toBe('invalid');
    expect(formatRelativeDate('')).toBe('');
  });
});
