import { useState } from 'react';
import type { PeriodAnalytics } from '../../types';
import { formatCurrency, getCategoryColor, getCategoryLabel } from '../../utils';
import { ExpenseTrendChart } from '../charts/ExpenseTrendChart';
import { CategoryDonutChart } from '../charts/CategoryDonutChart';
import { ExpenseComparison } from '../analytics/ExpenseComparison';
import { ExpenseHighlights } from '../analytics/ExpenseHighlights';

interface MyMoneyViewProps {
  analytics: PeriodAnalytics | null;
  loading: boolean;
  selectedYear: number;
  selectedMonth: number;
  onSelectMerchant?: (merchantName: string) => void;
  onSelectCategory?: (category: string | null) => void;
  onSelectDay?: (day: number) => void;
}

export function MyMoneyView({ analytics, loading, selectedYear, selectedMonth, onSelectMerchant, onSelectCategory, onSelectDay }: MyMoneyViewProps) {
  const [categoryView, setCategoryView] = useState<'donut' | 'bars'>('donut');
  const period = new Intl.DateTimeFormat('es-PE', { month: 'long', year: 'numeric' }).format(new Date(selectedYear, selectedMonth - 1, 1));

  return (
    <div className="space-y-6">
      <div>
        <p className="eyebrow">{period}</p>
        <h1 className="font-display mt-2 text-3xl font-semibold tracking-tight sm:text-4xl">Análisis</h1>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <ExpenseComparison analytics={analytics} loading={loading} />
        <ExpenseHighlights analytics={analytics} loading={loading} />
      </div>

      <section className="surface p-6 sm:p-7" aria-labelledby="expense-trend-heading">
        <div className="mb-5 border-b border-line pb-4">
          <p className="eyebrow">Evolución</p>
          <h2 id="expense-trend-heading" className="font-display mt-1 text-xl font-semibold">Gastos por día</h2>
          <p className="mt-1 text-sm text-muted">Consulta el gasto diario o el acumulado de {period}.</p>
        </div>
        {loading ? <p className="py-12 text-sm text-muted" role="status">Calculando evolución...</p> : <ExpenseTrendChart points={analytics?.dailyExpenses ?? []} monthLabel={period} onSelectDay={onSelectDay} />}
      </section>

      <div className="grid gap-6 lg:grid-cols-2">
        <section className="surface p-6 sm:p-7" aria-labelledby="category-analysis-heading">
          <div className="mb-5 flex flex-wrap items-end justify-between gap-3 border-b border-line pb-4">
            <div>
              <p className="eyebrow">Distribución</p>
              <h2 id="category-analysis-heading" className="font-display mt-1 text-xl font-semibold">Gastos por categoría</h2>
              <p className="mt-1 text-sm text-muted">Parte del gasto que corresponde a cada categoría.</p>
            </div>
            {analytics?.categoryBreakdown && analytics.categoryBreakdown.length > 0 && (
              <div role="group" aria-label="Vista de categorías" className="inline-flex rounded-xl border border-line bg-canvas p-1">
                {([['donut', 'Anillo'], ['bars', 'Barras']] as const).map(([value, label]) => (
                  <button
                    type="button"
                    key={value}
                    aria-pressed={categoryView === value}
                    onClick={() => setCategoryView(value)}
                    className={`rounded-lg px-3 py-1.5 text-xs font-semibold ${categoryView === value ? 'bg-white text-brand shadow-xs' : 'text-muted hover:text-ink'}`}
                  >
                    {label}
                  </button>
                ))}
              </div>
            )}
          </div>
          {loading ? (
            <p role="status" className="py-10 text-sm text-muted">Calculando categorías...</p>
          ) : !analytics?.categoryBreakdown ? (
            <p role="alert" className="py-10 text-sm text-muted">El desglose por categoría requiere la API actualizada.</p>
          ) : !analytics.categoryBreakdown.length ? (
            <p className="py-10 text-sm text-muted">No hay gastos registrados en este período.</p>
          ) : categoryView === 'donut' ? (
            <CategoryDonutChart
              categories={analytics.categoryBreakdown}
              totalAmount={analytics.categoryBreakdown.reduce((sum, item) => sum + item.amount, 0)}
              onSelectCategory={onSelectCategory}
            />
          ) : (
            <ol className="divide-y divide-line">
              {analytics.categoryBreakdown.map((item, idx) => {
                const label = getCategoryLabel(item.category);
                const color = getCategoryColor(item.category, idx);
                return (
                  <li key={JSON.stringify(item.category)}>
                    <button
                      type="button"
                      disabled={loading || !onSelectCategory}
                      onClick={() => onSelectCategory?.(item.category)}
                      aria-label={`Ver movimientos de la categoría ${label}`}
                      className="group block w-full rounded-lg py-4 text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand disabled:cursor-default"
                    >
                      <div className="flex items-center justify-between gap-4 text-sm">
                        <div className="flex min-w-0 items-center gap-2.5">
                          <span
                            className="h-2.5 w-2.5 shrink-0 rounded-full"
                            style={{ backgroundColor: color }}
                            aria-hidden="true"
                          />
                          <span className="min-w-0 break-words font-semibold text-brand group-hover:underline">
                            {label}
                          </span>
                        </div>
                        <span className="font-num shrink-0 font-semibold">{formatCurrency(item.amount)}</span>
                      </div>
                      <div className="mt-2.5 h-1.5 rounded-full bg-canvas" aria-hidden="true">
                        <div
                          className="h-full rounded-full"
                          style={{
                            width: `${Math.min(100, Math.max(0, item.percentage))}%`,
                            backgroundColor: color,
                          }}
                        />
                      </div>
                      <div className="font-num mt-2 flex justify-between text-xs text-muted">
                        <span>{item.count} {item.count === 1 ? 'gasto' : 'gastos'}</span>
                        <span>{item.percentage.toFixed(1)}% del gasto</span>
                      </div>
                    </button>
                  </li>
                );
              })}
            </ol>
          )}
        </section>

        <section className="surface p-6 sm:p-7" aria-labelledby="merchants-heading">
          <div className="border-b border-line pb-5">
            <p className="eyebrow">Concentración</p>
            <h2 id="merchants-heading" className="font-display mt-1 text-xl font-semibold">¿Dónde gastaste más?</h2>
            <p className="mt-1 text-sm text-muted">Comercios ordenados por gasto total.</p>
          </div>
          {loading && !analytics ? (
            <p className="py-10 text-sm text-muted" role="status">Calculando comercios...</p>
          ) : !analytics?.topMerchants.length ? (
            <p className="py-10 text-sm text-muted">No hay comercios registrados en este período.</p>
          ) : (
            <ol className="divide-y divide-line">
              {analytics.topMerchants.map((merchant, index) => (
                <li key={`${merchant.merchantName}-${index}`} className="flex items-center gap-4 py-4">
                  <span className="font-num w-6 shrink-0 text-sm font-medium text-muted">{String(index + 1).padStart(2, '0')}</span>
                  <span className="min-w-0 flex-1">
                    <button type="button" disabled={loading || !onSelectMerchant} onClick={() => onSelectMerchant?.(merchant.merchantName)}
                      aria-label={`Ver movimientos de ${merchant.merchantName || 'comercio sin nombre'}`}
                      className="block max-w-full truncate rounded-sm text-left text-sm font-semibold text-brand hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand disabled:cursor-default disabled:text-ink">
                      {merchant.merchantName || 'Comercio sin nombre'}
                    </button>
                    <span className="mt-1 block text-xs text-muted">{merchant.transactionCount} {merchant.transactionCount === 1 ? 'consumo' : 'consumos'} · {merchant.percentage.toFixed(1)}% del gasto</span>
                  </span>
                  <span className="font-num shrink-0 text-sm font-semibold">{formatCurrency(merchant.totalAmount)}</span>
                </li>
              ))}
            </ol>
          )}
        </section>
      </div>
    </div>
  );
}
