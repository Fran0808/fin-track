import type { PeriodAnalytics } from '../../types';
import { formatCurrency, formatDate } from '../../utils';

export function ExpenseHighlights({ analytics, loading }: { analytics: PeriodAnalytics | null; loading: boolean }) {
  const largest = analytics?.largestExpense;
  const peak = analytics?.peakExpenseDay;
  return <section aria-label="Datos destacados" className="surface flex flex-col justify-between p-6 sm:p-7">
    <div className="border-b border-line pb-4">
      <p className="eyebrow">Picos del período</p>
      <h2 className="font-display mt-1 text-xl font-semibold">Datos destacados</h2>
    </div>
    {loading ? <p role="status" className="pt-5 text-sm text-muted">Calculando datos destacados...</p>
      : largest === undefined || peak === undefined ? <p className="pt-5 text-sm text-muted">Datos destacados no disponibles para este período.</p>
      : !largest && !peak ? <p className="pt-5 text-sm text-muted">No hay gastos registrados para destacar en este período.</p>
      : <div className="grid gap-5 pt-5 sm:grid-cols-2 sm:divide-x sm:divide-line">
        <div>
          <h3 className="text-xs font-semibold uppercase tracking-wider text-muted">Mayor gasto registrado</h3>
          {largest ? <>
            <p className="font-display font-num mt-2 text-2xl font-semibold text-ink">{formatCurrency(largest.amount)}</p>
            <p className="mt-1.5 break-words text-sm font-semibold text-ink">{largest.merchantName || 'Comercio sin nombre'}</p>
            <p className="mt-0.5 text-xs text-muted">{formatDate(largest.transactionDate)}</p>
          </> : <p className="mt-2 text-sm text-muted">Sin gastos registrados.</p>}
        </div>
        <div className="sm:pl-5">
          <h3 className="text-xs font-semibold uppercase tracking-wider text-muted">Día con más gastos registrados</h3>
          {peak ? <>
            <p className="font-display font-num mt-2 text-2xl font-semibold text-ink">{formatCurrency(peak.amount)}</p>
            <p className="mt-1.5 text-sm font-semibold text-ink">{peak.date.slice(0, 10).split('-').reverse().join('/')}</p>
            <p className="mt-0.5 text-xs text-muted">Suma más alta en un día</p>
          </> : <p className="mt-2 text-sm text-muted">Sin gastos registrados.</p>}
        </div>
      </div>}
  </section>;
}
