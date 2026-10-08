import { useState } from 'react';
import type { TransactionFilterParams } from '../../services';

interface TransactionDateFilterProps {
  startDate?: string;
  endDate?: string;
  onFilterChange: (filters: Partial<TransactionFilterParams>) => void;
}

export function TransactionDateFilter({ startDate, endDate, onFilterChange }: TransactionDateFilterProps) {
  const start = startDate?.slice(0, 10) || '';
  const end = endDate?.slice(0, 10) || '';
  const [mode, setMode] = useState(start ? start === end ? 'day' : 'range' : 'month');
  const [from, setFrom] = useState(start);
  const [to, setTo] = useState(end);
  const invalidRange = mode === 'range' && Boolean(from && to && from > to);
  const canApply = Boolean(from && (mode === 'day' || to)) && !invalidRange;
  const inputClass = 'w-full rounded-xl border border-line bg-white px-3 py-2 text-sm text-ink focus:border-brand focus:outline-none';

  return (
    <fieldset className="rounded-2xl border border-line bg-canvas/60 p-4">
      <legend className="px-1 text-xs font-semibold text-muted">Fecha de los movimientos</legend>
      <div className="flex flex-wrap gap-2" aria-label="Tipo de búsqueda por fecha">
        {[
          ['month', 'Mes seleccionado'], ['day', 'Día específico'], ['range', 'Rango de fechas'],
        ].map(([value, label]) => (
          <button key={value} type="button" aria-pressed={mode === value}
            className={`rounded-lg border px-3 py-2 text-xs font-semibold focus-visible:outline-2 focus-visible:outline-brand ${mode === value ? 'border-brand bg-white text-brand' : 'border-line text-muted hover:text-ink'}`}
            onClick={() => {
              setMode(value);
              if (value === 'month') onFilterChange({ startDate: undefined, endDate: undefined });
            }}>
            {label}
          </button>
        ))}
      </div>
      {mode !== 'month' && (
        <div className="mt-3 grid items-end gap-3 sm:grid-cols-[1fr_1fr_auto]">
          <div>
            <label htmlFor="transaction-date-from" className="mb-1 block text-xs font-semibold text-muted">{mode === 'day' ? 'Fecha' : 'Desde'}</label>
            <input id="transaction-date-from" type="date" value={from} className={inputClass}
              onChange={(event) => setFrom(event.target.value)} />
          </div>
          {mode === 'range' && (
            <div>
              <label htmlFor="transaction-date-to" className="mb-1 block text-xs font-semibold text-muted">Hasta</label>
              <input id="transaction-date-to" type="date" value={to} min={from || undefined}
                aria-invalid={invalidRange} aria-describedby={invalidRange ? 'transaction-date-error' : undefined}
                className={inputClass} onChange={(event) => setTo(event.target.value)} />
            </div>
          )}
          <button type="button" disabled={!canApply}
            className="rounded-xl bg-brand px-4 py-2 text-sm font-semibold text-white focus-visible:outline-2 focus-visible:outline-brand disabled:cursor-not-allowed disabled:opacity-50"
            onClick={() => onFilterChange({
              startDate: `${from}T00:00:00`,
              endDate: `${mode === 'day' ? from : to}T23:59:59.999999`,
            })}>
            Buscar por fecha
          </button>
        </div>
      )}
      {invalidRange && <p id="transaction-date-error" role="alert" className="mt-2 text-sm text-negative">La fecha final debe ser igual o posterior a la inicial.</p>}
      <p className="mt-2 text-xs text-muted">{start ? `Búsqueda aplicada: ${start.split('-').reverse().join('/')} al ${end.split('-').reverse().join('/')}.` : 'Se muestran los movimientos del mes seleccionado.'}</p>
    </fieldset>
  );
}
