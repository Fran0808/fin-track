import type { PeriodAnalytics } from '../../types';
import { formatCurrency } from '../../utils';

function dateLabel(value: string) {
  return value.slice(0, 10).split('-').reverse().join('/');
}

export function ExpenseComparison({ analytics, loading }: { analytics: PeriodAnalytics | null; loading: boolean }) {
  const comparable = analytics?.periodStart && analytics.periodEnd && analytics.previousPeriodStart && analytics.previousPeriodEnd
    && analytics.previousPeriodExpense != null && analytics.expenseDifference != null;
  const currentExpense = analytics?.monthlyExpense ?? 0;
  const previousExpense = analytics?.previousPeriodExpense ?? 0;
  const difference = analytics?.expenseDifference ?? 0;
  const maxExpense = Math.max(1, currentExpense, previousExpense);
  const currentBarWidth = currentExpense > 0 ? Math.min(100, Math.max(4, (currentExpense / maxExpense) * 100)) : 0;
  const previousBarWidth = previousExpense > 0 ? Math.min(100, Math.max(4, (previousExpense / maxExpense) * 100)) : 0;
  const percentChange = previousExpense > 0 && difference !== 0
    ? (difference / previousExpense) * 100
    : null;

  return (
    <section className="surface flex flex-col p-6 sm:p-7" aria-labelledby="expense-comparison-heading">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-line pb-4">
        <div>
          <p className="eyebrow">Ritmo de gasto</p>
          <h2 id="expense-comparison-heading" className="font-display mt-1 text-xl font-semibold">Comparación de gastos registrados</h2>
        </div>
        {!loading && comparable && percentChange !== null && (
          <span
            className={`font-num inline-flex items-center rounded-full px-2.5 py-1 text-xs font-semibold ${
              difference > 0 ? 'bg-negative/10 text-negative' : 'bg-positive/10 text-positive'
            }`}
          >
            {difference > 0 ? '+' : ''}{percentChange.toFixed(1)}% vs. mes ant.
          </span>
        )}
      </div>
      {loading ? (
        <p role="status" className="pt-5 text-sm text-muted">Calculando comparación...</p>
      ) : !comparable ? (
        <p className="pt-5 text-sm text-muted">Comparación no disponible para este período.</p>
      ) : (
        <div className="flex flex-1 flex-col justify-between pt-5">
          <div className="space-y-3.5">
            <div>
              <div className="flex items-baseline justify-between gap-3">
                <div>
                  <h3 className="text-xs font-semibold uppercase tracking-wider text-muted">Período seleccionado</h3>
                  <p className="mt-0.5 text-xs text-muted">{dateLabel(analytics.periodStart!)} al {dateLabel(analytics.periodEnd!)}</p>
                </div>
                <p className="font-display font-num text-xl font-semibold text-ink sm:text-2xl">{formatCurrency(currentExpense)}</p>
              </div>
              <div className="mt-2 h-2 rounded-full bg-canvas" aria-hidden="true">
                <div
                  data-testid="current-period-bar"
                  className="h-full rounded-full bg-brand transition-all duration-300"
                  style={{ width: `${currentBarWidth}%` }}
                />
              </div>
            </div>

            <div>
              <div className="flex items-baseline justify-between gap-3">
                <div>
                  <h3 className="text-xs font-semibold uppercase tracking-wider text-muted">Mes anterior</h3>
                  <p className="mt-0.5 text-xs text-muted">{dateLabel(analytics.previousPeriodStart!)} al {dateLabel(analytics.previousPeriodEnd!)}</p>
                </div>
                <p className="font-display font-num text-xl font-semibold text-ink sm:text-2xl">{formatCurrency(previousExpense)}</p>
              </div>
              <div className="mt-2 h-2 rounded-full bg-canvas" aria-hidden="true">
                <div
                  data-testid="previous-period-bar"
                  className="h-full rounded-full bg-muted/45 transition-all duration-300"
                  style={{ width: `${previousBarWidth}%` }}
                />
              </div>
            </div>
          </div>

          <div className="mt-4 flex items-baseline justify-between gap-3 border-t border-line pt-3.5">
            <h3 className="text-xs font-semibold uppercase tracking-wider text-muted">Diferencia registrada</h3>
            <p className={`font-display font-num text-lg font-semibold sm:text-xl ${difference > 0 ? 'text-negative' : difference < 0 ? 'text-positive' : 'text-ink'}`}>
              {difference === 0 ? 'Sin diferencia' : `${formatCurrency(Math.abs(difference))} ${difference > 0 ? 'más' : 'menos'}`}
            </p>
          </div>
        </div>
      )}
    </section>
  );
}
