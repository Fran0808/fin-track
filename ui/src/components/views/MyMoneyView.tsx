import { ArrowDownLeft, ArrowLeftRight, ArrowUpRight } from 'lucide-react';
import type { PeriodAnalytics } from '../../types';
import { STANDARD_CATEGORIES } from '../../types';
import { formatCurrency, getChannelLabel } from '../../utils';
import { ExpenseTrendChart } from '../charts/ExpenseTrendChart';

interface MyMoneyViewProps {
  analytics: PeriodAnalytics | null;
  loading: boolean;
  selectedYear: number;
  selectedMonth: number;
  onSelectMerchant?: (merchantName: string) => void;
  onSelectCategory?: (category: string | null) => void;
}

export function MyMoneyView({ analytics, loading, selectedYear, selectedMonth, onSelectMerchant, onSelectCategory }: MyMoneyViewProps) {
  const expense = analytics?.monthlyExpense ?? 0;
  const income = analytics?.monthlyIncome ?? 0;
  const net = income - expense;
  const period = new Intl.DateTimeFormat('es-PE', { month: 'long', year: 'numeric' }).format(new Date(selectedYear, selectedMonth - 1, 1));

  return (
    <div className="space-y-8">
      <div>
        <p className="eyebrow">{period}</p>
        <h1 className="font-display mt-2 text-3xl font-semibold tracking-tight sm:text-4xl">Análisis</h1>
      </div>

      <section className="surface grid divide-y divide-line md:grid-cols-3 md:divide-x md:divide-y-0" aria-label="Flujo de caja del período">
        <div className="p-6 sm:p-7">
          <div className="flex items-center gap-2 text-negative"><ArrowUpRight className="h-4 w-4" /><span className="eyebrow !text-negative">Salidas</span></div>
          <p className="font-display font-num mt-4 text-3xl font-semibold tracking-tight">{loading ? 'S/ …' : formatCurrency(expense)}</p>
        </div>
        <div className="p-6 sm:p-7">
          <div className="flex items-center gap-2 text-positive"><ArrowDownLeft className="h-4 w-4" /><span className="eyebrow !text-positive">Entradas</span></div>
          <p className="font-display font-num mt-4 text-3xl font-semibold tracking-tight">{loading ? 'S/ …' : formatCurrency(income)}</p>
        </div>
        <div className="p-6 sm:p-7">
          <div className="flex items-center gap-2 text-brand"><ArrowLeftRight className="h-4 w-4" /><span className="eyebrow !text-brand">Flujo neto</span></div>
          <p className={`font-display font-num mt-4 text-3xl font-semibold tracking-tight ${net < 0 ? 'text-negative' : ''}`}>{loading ? 'S/ …' : formatCurrency(net)}</p>
          <p className="mt-2 text-sm text-muted">Entradas menos salidas registradas</p>
        </div>
      </section>

      <section className="surface p-6 sm:p-7" aria-labelledby="expense-trend-heading">
        <div className="mb-6 border-b border-line pb-5">
          <p className="eyebrow">Evolución</p>
          <h2 id="expense-trend-heading" className="font-display mt-1 text-xl font-semibold">Gastos por día</h2>
          <p className="mt-1 text-sm text-muted">Consulta el gasto diario o el acumulado de {period}.</p>
        </div>
        {loading ? <p className="py-12 text-sm text-muted" role="status">Calculando evolución...</p> : <ExpenseTrendChart points={analytics?.dailyExpenses ?? []} monthLabel={period} />}
      </section>

      <section className="surface p-6 sm:p-7" aria-labelledby="category-analysis-heading">
        <div className="border-b border-line pb-5">
          <h2 id="category-analysis-heading" className="font-display text-xl font-semibold">Gastos por categoría</h2>
        </div>
        {loading ? <p role="status" className="py-10 text-sm text-muted">Calculando categorías...</p>
          : !analytics?.categoryBreakdown ? <p role="alert" className="py-10 text-sm text-muted">El desglose por categoría requiere la API actualizada.</p>
          : !analytics.categoryBreakdown.length ? <p className="py-10 text-sm text-muted">No hay gastos registrados en este período.</p>
          : <ol className="divide-y divide-line">
            {analytics.categoryBreakdown.map(item => <li key={JSON.stringify(item.category)}>
              <button type="button" disabled={loading || !onSelectCategory} onClick={() => onSelectCategory?.(item.category)}
                aria-label={`Ver movimientos de la categoría ${item.category ? STANDARD_CATEGORIES.find(category => category.id === item.category)?.label ?? item.category : 'Sin categoría'}`}
                className="group block w-full rounded-lg py-4 text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand disabled:cursor-default">
              <div className="flex items-center justify-between gap-4 text-sm">
                <span className="min-w-0 break-words font-semibold text-brand group-hover:underline">{item.category ? STANDARD_CATEGORIES.find(category => category.id === item.category)?.label ?? item.category : 'Sin categoría'}</span>
                <span className="font-num shrink-0 font-semibold">{formatCurrency(item.amount)}</span>
              </div>
              <div className="mt-2.5 h-1.5 rounded-full bg-canvas" aria-hidden="true"><div className="h-full rounded-full bg-brand" style={{ width: `${Math.min(100, Math.max(0, item.percentage))}%` }} /></div>
              <div className="font-num mt-2 flex justify-between text-xs text-muted"><span>{item.count} {item.count === 1 ? 'gasto' : 'gastos'}</span><span>{item.percentage.toFixed(1)}% del gasto</span></div>
              </button>
            </li>)}
          </ol>}
      </section>

      <div className="grid gap-6 lg:grid-cols-2">
        <section className="surface p-6 sm:p-7" aria-labelledby="channel-analysis-heading">
          <div className="border-b border-line pb-5">
            <p className="eyebrow">Distribución</p>
            <h2 id="channel-analysis-heading" className="font-display mt-1 text-xl font-semibold">¿Cómo pagaste?</h2>
            <p className="mt-1 text-sm text-muted">Parte del gasto que corresponde a cada medio.</p>
          </div>
          {loading && !analytics ? (
            <p className="py-10 text-sm text-muted" role="status">Calculando distribución...</p>
          ) : !analytics?.channelBreakdown.length ? (
            <p className="py-10 text-sm text-muted">No hay gastos registrados en este período.</p>
          ) : (
            <div className="divide-y divide-line">
              {analytics.channelBreakdown.map((channel, index) => {
                const isWallet = channel.channel.includes('YAPE') || channel.channel.includes('PLIN');
                return (
                  <div key={`${channel.channel}-${channel.cardLast4 || index}`} className="py-4">
                    <div className="flex items-center justify-between gap-3">
                      <span className="min-w-0 truncate text-sm font-semibold">{channel.displayName?.replace(/\*\*/g, '··') || getChannelLabel(channel.channel)}</span>
                      <span className="font-num shrink-0 text-sm font-semibold">{formatCurrency(channel.amount)}</span>
                    </div>
                    <div className="mt-2.5 h-1.5 rounded-full bg-canvas">
                      <div className={`h-full rounded-full ${isWallet ? 'bg-yape' : 'bg-brand'}`} style={{ width: `${Math.min(100, Math.max(0, channel.percentage))}%` }} />
                    </div>
                    <div className="font-num mt-2 flex justify-between text-xs text-muted">
                      <span>{channel.count} {channel.count === 1 ? 'movimiento' : 'movimientos'}</span>
                      <span>{channel.percentage.toFixed(1)}%</span>
                    </div>
                  </div>
                );
              })}
            </div>
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

      <p className="text-sm leading-relaxed text-muted">
        {loading ? 'Calculando transferencias propias...' : `${formatCurrency(analytics?.internalTransfersAmount ?? 0)} en transferencias entre tus cuentas quedan fuera del total de gastos.`}
      </p>
    </div>
  );
}
