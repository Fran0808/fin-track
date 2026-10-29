import type { PeriodAnalytics } from '../../types';
import { formatCurrency } from '../../utils';

function dateLabel(value: string) {
  return value.slice(0, 10).split('-').reverse().join('/');
}

export function ExpenseComparison({ analytics, loading }: { analytics: PeriodAnalytics | null; loading: boolean }) {
  const comparable = analytics?.periodStart && analytics.periodEnd && analytics.previousPeriodStart && analytics.previousPeriodEnd
    && analytics.previousPeriodExpense != null && analytics.expenseDifference != null;
  const difference = analytics?.expenseDifference ?? 0;
  return <section className="surface flex flex-col justify-between p-6 sm:p-7" aria-labelledby="expense-comparison-heading">
    <div className="border-b border-line pb-4">
      <p className="eyebrow">Ritmo de gasto</p>
      <h2 id="expense-comparison-heading" className="font-display mt-1 text-xl font-semibold">Comparación de gastos registrados</h2>
    </div>
    {loading ? <p role="status" className="pt-5 text-sm text-muted">Calculando comparación...</p>
      : !comparable ? <p className="pt-5 text-sm text-muted">Comparación no disponible para este período.</p>
      : <div className="grid gap-5 pt-5 sm:grid-cols-3 sm:divide-x sm:divide-line">
        <div>
          <h3 className="text-xs font-semibold uppercase tracking-wider text-muted">Período seleccionado</h3>
          <p className="font-display font-num mt-2 text-2xl font-semibold text-ink">{formatCurrency(analytics.monthlyExpense)}</p>
          <p className="mt-1.5 text-xs text-muted">{dateLabel(analytics.periodStart!)} al {dateLabel(analytics.periodEnd!)}</p>
        </div>
        <div className="sm:pl-5">
          <h3 className="text-xs font-semibold uppercase tracking-wider text-muted">Mes anterior</h3>
          <p className="font-display font-num mt-2 text-2xl font-semibold text-ink">{formatCurrency(analytics.previousPeriodExpense!)}</p>
          <p className="mt-1.5 text-xs text-muted">{dateLabel(analytics.previousPeriodStart!)} al {dateLabel(analytics.previousPeriodEnd!)}</p>
        </div>
        <div className="sm:pl-5">
          <h3 className="text-xs font-semibold uppercase tracking-wider text-muted">Diferencia registrada</h3>
          <p className={`font-display font-num mt-2 text-2xl font-semibold ${difference > 0 ? 'text-negative' : difference < 0 ? 'text-positive' : 'text-ink'}`}>
            {difference === 0 ? 'Sin diferencia' : `${formatCurrency(Math.abs(difference))} ${difference > 0 ? 'más' : 'menos'}`}
          </p>
        </div>
      </div>}
  </section>;
}
