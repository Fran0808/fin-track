import type { PeriodAnalytics } from '../../types';
import { formatCurrency } from '../../utils';

function dateLabel(value: string) {
  return value.slice(0, 10).split('-').reverse().join('/');
}

export function ExpenseComparison({ analytics, loading }: { analytics: PeriodAnalytics | null; loading: boolean }) {
  const comparable = analytics?.periodStart && analytics.periodEnd && analytics.previousPeriodStart && analytics.previousPeriodEnd
    && analytics.previousPeriodExpense != null && analytics.expenseDifference != null;
  const difference = analytics?.expenseDifference ?? 0;
  return <section className="surface p-6 sm:p-7" aria-labelledby="expense-comparison-heading">
    <h2 id="expense-comparison-heading" className="font-display text-xl font-semibold">Comparación de gastos registrados</h2>
    {loading ? <p role="status" className="mt-5 text-sm text-muted">Calculando comparación...</p>
      : !comparable ? <p className="mt-5 text-sm text-muted">Comparación no disponible para este período.</p>
      : <div className="mt-5 grid gap-5 md:grid-cols-3">
        <div>
          <h3 className="text-sm font-medium text-muted">Período seleccionado</h3>
          <p className="font-num mt-2 text-2xl font-semibold">{formatCurrency(analytics.monthlyExpense)}</p>
          <p className="mt-2 text-xs text-muted">{dateLabel(analytics.periodStart!)} al {dateLabel(analytics.periodEnd!)}</p>
        </div>
        <div>
          <h3 className="text-sm font-medium text-muted">Mes anterior</h3>
          <p className="font-num mt-2 text-2xl font-semibold">{formatCurrency(analytics.previousPeriodExpense!)}</p>
          <p className="mt-2 text-xs text-muted">{dateLabel(analytics.previousPeriodStart!)} al {dateLabel(analytics.previousPeriodEnd!)}</p>
        </div>
        <div>
          <h3 className="text-sm font-medium text-muted">Diferencia registrada</h3>
          <p className={`font-num mt-2 text-2xl font-semibold ${difference > 0 ? 'text-negative' : difference < 0 ? 'text-positive' : 'text-ink'}`}>
            {difference === 0 ? 'Sin diferencia' : `${formatCurrency(Math.abs(difference))} ${difference > 0 ? 'más' : 'menos'}`}
          </p>
        </div>
      </div>}
  </section>;
}
