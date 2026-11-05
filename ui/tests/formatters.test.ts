import { afterEach, describe, expect, it, vi } from 'vitest';
import { formatDate, formatRelativeDate, formatExportFilename } from '../src/utils/formatters';

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

describe('formatExportFilename', () => {
  const fixedNow = new Date('2026-11-04T12:00:00Z');

  it('names standard monthly exports by month and year', () => {
    expect(formatExportFilename({}, '10/2026', fixedNow)).toBe('fintrack_movimientos_octubre_2026.csv');
    expect(formatExportFilename({}, '01/2026', fixedNow)).toBe('fintrack_movimientos_enero_2026.csv');
  });

  it('includes merchant or category slug when filtered', () => {
    expect(formatExportFilename({ merchantName: 'Starbucks' }, '10/2026', fixedNow)).toBe('fintrack_movimientos_starbucks_octubre_2026.csv');
    expect(formatExportFilename({ categoryGroup: 'Comida y bebidas' }, '10/2026', fixedNow)).toBe('fintrack_movimientos_comida_y_bebidas_octubre_2026.csv');
  });

  it('includes flow type in filename when filtering by expense, income or transfer', () => {
    expect(formatExportFilename({ flowType: 'EXPENSE' }, '10/2026', fixedNow)).toBe('fintrack_movimientos_gastos_octubre_2026.csv');
    expect(formatExportFilename({ flowType: 'INCOME' }, '10/2026', fixedNow)).toBe('fintrack_movimientos_entradas_octubre_2026.csv');
    expect(formatExportFilename({ flowType: 'INTERNAL_TRANSFER' }, '10/2026', fixedNow)).toBe('fintrack_movimientos_transferencias_octubre_2026.csv');
  });

  it('names custom date ranges and single days descriptively', () => {
    expect(formatExportFilename({ startDate: '2026-10-01T00:00:00', endDate: '2026-10-07T23:59:59' }, undefined, fixedNow)).toBe('fintrack_movimientos_2026-10-01_al_2026-10-07.csv');
    expect(formatExportFilename({ startDate: '2026-10-15T00:00:00', endDate: '2026-10-15T23:59:59' }, undefined, fixedNow)).toBe('fintrack_movimientos_2026-10-15.csv');
  });

  it('names complete historical export with current date stamp', () => {
    expect(formatExportFilename({ allHistory: true }, undefined, fixedNow)).toBe('fintrack_movimientos_historico_2026-11-04.csv');
  });
});
