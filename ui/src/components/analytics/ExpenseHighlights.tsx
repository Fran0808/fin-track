import type { PeriodAnalytics } from '../../types';
import { formatCurrency, formatDate } from '../../utils';

export function ExpenseHighlights({ analytics, loading }: { analytics: PeriodAnalytics | null; loading: boolean }) {
  const largest = analytics?.largestExpense;
  const peak = analytics?.peakExpenseDay;
  return <section aria-label="Datos destacados" className="border-y border-line py-5">
    {loading ? <p role="status" className="text-sm text-muted">Calculando datos destacados...</p>
      : largest === undefined || peak === undefined ? <p className="text-sm text-muted">Datos destacados no disponibles para este período.</p>
      : !largest && !peak ? <p className="text-sm text-muted">No hay gastos registrados para destacar en este período.</p>
      : <div className="grid gap-6 sm:grid-cols-2">
        <div>
          <h2 className="text-sm font-medium text-muted">Mayor gasto registrado</h2>
          {largest ? <>
            <p className="font-display font-num mt-2 text-2xl font-semibold">{formatCurrency(largest.amount)}</p>
            <p className="mt-2 break-words text-sm font-medium">{largest.merchantName || 'Comercio sin nombre'}</p>
            <p className="mt-1 text-xs text-muted">{formatDate(largest.transactionDate)}</p>
          </> : <p className="mt-2 text-sm text-muted">Sin gastos registrados.</p>}
        </div>
        <div>
          <h2 className="text-sm font-medium text-muted">Día con más gastos registrados</h2>
          {peak ? <>
            <p className="font-display font-num mt-2 text-2xl font-semibold">{formatCurrency(peak.amount)}</p>
            <p className="mt-2 text-sm font-medium">{peak.date.slice(0, 10).split('-').reverse().join('/')}</p>
          </> : <p className="mt-2 text-sm text-muted">Sin gastos registrados.</p>}
        </div>
      </div>}
  </section>;
}
