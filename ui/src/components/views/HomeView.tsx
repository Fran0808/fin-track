import { ArrowDownLeft, ArrowRight, ArrowUpRight, ArrowLeftRight, CreditCard, Smartphone } from 'lucide-react';
import type { PeriodAnalytics, Transaction } from '../../types';
import { formatCurrency, formatRelativeDate, getChannelLabel } from '../../utils';
import { getCategoryLabel } from '../../utils/categoryColors';

interface HomeViewProps {
  analytics: PeriodAnalytics | null;
  recentTransactions: Transaction[];
  loading: boolean;
  onNavigateToTransactions: () => void;
  onSelectTransaction: (transaction: Transaction) => void;
  selectedYear: number;
  selectedMonth: number;
  animateIntro: boolean;
  onIntroComplete: () => void;
}

const MONTHS = [
  'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio',
  'julio', 'agosto', 'setiembre', 'octubre', 'noviembre', 'diciembre',
];

function formatVariationSubtitle(current: number, previous: number | null | undefined, fallback: string): string {
  if (previous == null || previous <= 0) return fallback;
  const diff = current - previous;
  if (diff === 0) return 'Sin variación respecto al mes anterior';
  const pct = (diff / previous) * 100;
  return `${pct > 0 ? '+' : ''}${pct.toFixed(1)}% respecto al mes anterior`;
}

export function HomeView({
  analytics,
  recentTransactions,
  loading,
  onNavigateToTransactions,
  onSelectTransaction,
  selectedYear,
  selectedMonth,
  animateIntro,
  onIntroComplete,
}: HomeViewProps) {
  const expense = analytics?.monthlyExpense ?? 0;
  const income = analytics?.monthlyIncome ?? 0;
  const period = `${MONTHS[selectedMonth - 1]} de ${selectedYear}`;

  const expenseSubtitle = formatVariationSubtitle(expense, analytics?.previousPeriodExpense, 'Salidas registradas en el período');
  const incomeSubtitle = formatVariationSubtitle(income, analytics?.previousPeriodIncome, 'Entradas registradas en el período');

  const daysElapsed = analytics?.dailyExpenses?.length ?? 0;
  const dailyAverage = daysElapsed > 0 ? expense / daysElapsed : 0;
  const topCategory = analytics?.categoryBreakdown?.[0] ?? null;
  const totalMovements = analytics?.totalMovements ?? 0;

  return (
    <div className="space-y-8">
      <div>
        <h1 className="font-display text-3xl font-semibold tracking-tight text-ink sm:text-4xl">Resumen mensual</h1>
        <p className="mt-2 text-sm capitalize text-muted">{period}</p>
      </div>

      <section
        className={`surface overflow-hidden${animateIntro && !loading ? ' summary-intro' : ''}`}
        aria-label="Resumen financiero mensual"
        onAnimationEnd={(event) => {
          if (event.target === event.currentTarget) onIntroComplete();
        }}
      >
        <div className="grid divide-y divide-line sm:grid-cols-2 sm:divide-x sm:divide-y-0">
          <div className="p-6 sm:p-7">
            <div className="flex items-center gap-2 text-ink">
              <ArrowUpRight className="h-4 w-4 text-negative" />
              <p className="eyebrow">Gastos del mes</p>
            </div>
            <p className="font-display font-num mt-3 text-2xl font-semibold tracking-tight text-ink sm:text-3xl">
              {loading ? 'S/ …' : formatCurrency(expense)}
            </p>
            <p className="font-num mt-1.5 text-xs text-muted">{loading ? 'Calculando…' : expenseSubtitle}</p>
            {!loading && (
              <div className="font-num mt-4 flex flex-wrap items-center gap-x-4 gap-y-1 border-t border-line/70 pt-3 text-xs text-muted">
                {daysElapsed > 0 && (
                  <span>Promedio diario: <strong className="font-semibold text-ink">{formatCurrency(dailyAverage)}</strong></span>
                )}
                {topCategory ? (
                  <span>Mayor rubro: <strong className="font-semibold text-ink">{getCategoryLabel(topCategory.category)} ({topCategory.percentage.toFixed(0)}%)</strong></span>
                ) : analytics?.previousPeriodExpense != null ? (
                  <span>Mes anterior: <strong className="font-semibold text-ink">{formatCurrency(analytics.previousPeriodExpense)}</strong></span>
                ) : null}
              </div>
            )}
          </div>

          <div className="p-6 sm:p-7">
            <div className="flex items-center gap-2 text-positive">
              <ArrowDownLeft className="h-4 w-4" />
              <p className="eyebrow !text-positive">Entradas del mes</p>
            </div>
            <p className="font-display font-num mt-3 text-2xl font-semibold tracking-tight text-positive sm:text-3xl">
              {loading ? 'S/ …' : formatCurrency(income)}
            </p>
            <p className="font-num mt-1.5 text-xs text-muted">{loading ? 'Calculando…' : incomeSubtitle}</p>
            {!loading && (
              <div className="font-num mt-4 flex flex-wrap items-center gap-x-4 gap-y-1 border-t border-line/70 pt-3 text-xs text-muted">
                {analytics?.previousPeriodIncome != null && (
                  <span>Mes anterior: <strong className="font-semibold text-ink">{formatCurrency(analytics.previousPeriodIncome)}</strong></span>
                )}
                <span><strong className="font-semibold text-ink">{totalMovements}</strong> {totalMovements === 1 ? 'movimiento en el mes' : 'movimientos en el mes'}</span>
              </div>
            )}
          </div>
        </div>
        <div className="h-1.5 w-full bg-brand" aria-hidden="true" />
      </section>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.7fr)_minmax(300px,1fr)]">
        <section className="surface min-w-0" aria-labelledby="recent-heading">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line px-6 py-5">
            <div>
              <h2 id="recent-heading" className="font-display text-lg font-semibold">Movimientos recientes</h2>
            </div>
            <button type="button" onClick={onNavigateToTransactions} className="inline-flex items-center gap-1.5 text-sm font-semibold text-brand hover:underline">
              Ver todos <ArrowRight className="h-4 w-4" />
            </button>
          </div>
          {loading && recentTransactions.length === 0 ? (
            <p className="px-6 py-12 text-sm text-muted" role="status">Cargando movimientos...</p>
          ) : recentTransactions.length === 0 ? (
            <p className="px-6 py-12 text-sm text-muted">Aún no hay movimientos en este período.</p>
          ) : (
            <div className="divide-y divide-line">
              {recentTransactions.slice(0, 5).map((transaction) => {
                const isIncome = transaction.flowType === 'INCOME';
                const isTransfer = transaction.flowType === 'INTERNAL_TRANSFER';
                const Icon = isIncome ? ArrowDownLeft : isTransfer ? ArrowLeftRight : ArrowUpRight;
                return (
                  <button
                    key={transaction.id}
                    type="button"
                    onClick={() => onSelectTransaction(transaction)}
                    className="flex w-full items-center justify-between gap-4 px-6 py-4 text-left transition-colors hover:bg-canvas"
                  >
                    <span className="flex min-w-0 items-center gap-3">
                      <span className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ${isIncome ? 'bg-positive/10 text-positive' : isTransfer ? 'bg-brand/10 text-brand' : 'bg-negative/10 text-negative'}`}>
                        <Icon className="h-[18px] w-[18px]" />
                      </span>
                      <span className="min-w-0">
                        <span className="block truncate text-sm font-semibold text-ink">{transaction.contactName || 'Movimiento'}</span>
                        <span className="mt-0.5 block truncate text-xs text-muted">{getChannelLabel(transaction.channel)} · {formatRelativeDate(transaction.transactionDate)}</span>
                      </span>
                    </span>
                    <span className={`font-num shrink-0 text-sm font-semibold ${isIncome ? 'text-positive' : isTransfer ? 'text-muted' : 'text-ink'}`}>
                      {isIncome ? '+ ' : isTransfer ? '' : '− '}{formatCurrency(transaction.amount)}
                    </span>
                  </button>
                );
              })}
            </div>
          )}
        </section>

        <section className="surface p-6" aria-labelledby="channels-heading">
          <div className="border-b border-line pb-5">
            <h2 id="channels-heading" className="font-display text-lg font-semibold">Medios de pago</h2>
          </div>
          {loading && !analytics ? (
            <p className="py-10 text-sm text-muted" role="status">Calculando medios de pago...</p>
          ) : !analytics?.channelBreakdown.length ? (
            <p className="py-10 text-sm text-muted">Aún no hay gastos por medio de pago.</p>
          ) : (
            <div className="mt-5 space-y-6">
              {analytics.channelBreakdown.slice(0, 5).map((channel, index) => {
                const isWallet = channel.channel.includes('YAPE') || channel.channel.includes('PLIN');
                const Icon = isWallet ? Smartphone : CreditCard;
                return (
                  <div key={`${channel.channel}-${channel.cardLast4 || index}`}>
                    <div className="flex items-start justify-between gap-3">
                      <span className="flex min-w-0 items-center gap-2.5 text-sm font-medium">
                        <Icon className={`h-4 w-4 shrink-0 ${isWallet ? 'text-yape' : 'text-brand'}`} />
                        <span className="truncate">{channel.displayName?.replace(/\*\*/g, '··') || getChannelLabel(channel.channel)}</span>
                      </span>
                      <span className="font-num shrink-0 text-sm font-semibold">{formatCurrency(channel.amount)}</span>
                    </div>
                    <div className="mt-2.5 h-1.5 overflow-hidden rounded-full bg-canvas">
                      <div className={`h-full rounded-full ${isWallet ? 'bg-yape' : 'bg-brand'}`} style={{ width: `${Math.min(100, Math.max(0, channel.percentage))}%` }} />
                    </div>
                    <p className="font-num mt-1.5 text-right text-xs text-muted">{channel.percentage.toFixed(1)}%</p>
                  </div>
                );
              })}
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
