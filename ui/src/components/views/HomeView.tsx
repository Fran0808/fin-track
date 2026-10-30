import { ArrowDownLeft, ArrowRight, ArrowUpRight, ArrowLeftRight, CreditCard, Smartphone, Scale } from 'lucide-react';
import type { PeriodAnalytics, Transaction } from '../../types';
import { formatCurrency, formatRelativeDate, getChannelLabel } from '../../utils';
import { getCategoryColor, getCategoryLabel } from '../../utils/categoryColors';

interface HomeViewProps {
  analytics: PeriodAnalytics | null;
  recentTransactions: Transaction[];
  loading: boolean;
  onNavigateToTransactions: () => void;
  onSelectTransaction: (transaction: Transaction) => void;
  onSelectChannel?: (channel: string) => void;
  selectedYear: number;
  selectedMonth: number;
  animateIntro: boolean;
  onIntroComplete: () => void;
}

const MONTHS = [
  'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio',
  'julio', 'agosto', 'setiembre', 'octubre', 'noviembre', 'diciembre',
];

interface VariationData {
  pct: number;
  label: string;
}

function getVariation(current: number, previous: number | null | undefined): VariationData | null {
  if (previous == null || previous <= 0) return null;
  const diff = current - previous;
  if (diff === 0) return { pct: 0, label: '0.0% vs. mes ant.' };
  const pct = (diff / previous) * 100;
  return { pct, label: `${pct > 0 ? '+' : ''}${pct.toFixed(1)}% vs. mes ant.` };
}

export function HomeView({
  analytics,
  recentTransactions,
  loading,
  onNavigateToTransactions,
  onSelectTransaction,
  onSelectChannel,
  selectedYear,
  selectedMonth,
  animateIntro,
  onIntroComplete,
}: HomeViewProps) {
  const expense = analytics?.monthlyExpense ?? 0;
  const income = analytics?.monthlyIncome ?? 0;
  const period = `${MONTHS[selectedMonth - 1]} de ${selectedYear}`;

  const expenseVar = getVariation(expense, analytics?.previousPeriodExpense);
  const incomeVar = getVariation(income, analytics?.previousPeriodIncome);

  const daysElapsed = analytics?.dailyExpenses?.length ?? 0;
  const dailyAverage = daysElapsed > 0 ? expense / daysElapsed : 0;
  const topCategory = analytics?.categoryBreakdown?.[0] ?? null;
  const totalMovements = analytics?.totalMovements ?? 0;

  const netCents = Math.round(income * 100) - Math.round(expense * 100);
  const netBalance = netCents / 100;
  const isPositive = netCents > 0;
  const isNegative = netCents < 0;

  const prevIncome = analytics?.previousPeriodIncome;
  const prevExpense = analytics?.previousPeriodExpense;
  const hasPrevNet = prevIncome != null && prevExpense != null;
  const prevNetBalance = hasPrevNet ? (Math.round(prevIncome * 100) - Math.round(prevExpense * 100)) / 100 : null;

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
        <div className="grid divide-y divide-line md:grid-cols-3 md:divide-x md:divide-y-0">
          <div className="p-6 sm:p-7">
            <div className="flex items-center justify-between gap-3">
              <div className="flex items-center gap-2 text-ink">
                <ArrowUpRight className="h-4 w-4 text-negative" />
                <p className="eyebrow">Gastos del mes</p>
              </div>
              {!loading && expenseVar && (
                <span
                  className={`font-num inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                    expenseVar.pct > 0
                      ? 'bg-negative/10 text-negative'
                      : expenseVar.pct < 0
                      ? 'bg-positive/10 text-positive'
                      : 'bg-canvas text-muted'
                  }`}
                >
                  {expenseVar.label}
                </span>
              )}
            </div>
            <p className="font-display font-num mt-3 text-2xl font-semibold tracking-tight text-ink sm:text-3xl">
              {loading ? 'S/ …' : formatCurrency(expense)}
            </p>
            <p className="font-num mt-1.5 text-xs text-muted">
              {loading
                ? 'Calculando…'
                : analytics?.previousPeriodExpense != null
                ? `Mes anterior: ${formatCurrency(analytics.previousPeriodExpense)}`
                : 'Salidas registradas en el período'}
            </p>
            {!loading && (
              <div className="font-num mt-4 flex flex-wrap items-center gap-x-4 gap-y-1 border-t border-line/70 pt-3 text-xs text-muted">
                {daysElapsed > 0 && (
                  <span>Promedio diario: <strong className="font-semibold text-ink">{formatCurrency(dailyAverage)}</strong></span>
                )}
                {topCategory && (
                  <span>Mayor rubro: <strong className="font-semibold text-ink">{getCategoryLabel(topCategory.category)} ({topCategory.percentage.toFixed(0)}%)</strong></span>
                )}
              </div>
            )}
          </div>

          <div className="p-6 sm:p-7">
            <div className="flex items-center justify-between gap-3">
              <div className="flex items-center gap-2 text-positive">
                <ArrowDownLeft className="h-4 w-4" />
                <p className="eyebrow !text-positive">Entradas del mes</p>
              </div>
              {!loading && incomeVar && (
                <span
                  className={`font-num inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                    incomeVar.pct > 0
                      ? 'bg-positive/10 text-positive'
                      : incomeVar.pct < 0
                      ? 'bg-negative/10 text-negative'
                      : 'bg-canvas text-muted'
                  }`}
                >
                  {incomeVar.label}
                </span>
              )}
            </div>
            <p className="font-display font-num mt-3 text-2xl font-semibold tracking-tight text-positive sm:text-3xl">
              {loading ? 'S/ …' : formatCurrency(income)}
            </p>
            <p className="font-num mt-1.5 text-xs text-muted">
              {loading
                ? 'Calculando…'
                : analytics?.previousPeriodIncome != null
                ? `Mes anterior: ${formatCurrency(analytics.previousPeriodIncome)}`
                : 'Entradas registradas en el período'}
            </p>
            {!loading && (
              <div className="font-num mt-4 flex flex-wrap items-center gap-x-4 gap-y-1 border-t border-line/70 pt-3 text-xs text-muted">
                <span><strong className="font-semibold text-ink">{totalMovements}</strong> {totalMovements === 1 ? 'movimiento en el mes' : 'movimientos en el mes'}</span>
              </div>
            )}
          </div>

          <div className="p-6 sm:p-7">
            <div className="flex items-center justify-between gap-3">
              <div className="flex items-center gap-2 text-ink">
                <Scale className={`h-4 w-4 ${isPositive ? 'text-positive' : isNegative ? 'text-negative' : 'text-muted'}`} />
                <p className="eyebrow">Balance neto</p>
              </div>
              {!loading && (
                <span
                  className={`font-num inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                    isPositive
                      ? 'bg-positive/10 text-positive'
                      : isNegative
                      ? 'bg-negative/10 text-negative'
                      : 'bg-canvas text-muted'
                  }`}
                >
                  {isPositive ? 'A favor' : isNegative ? 'En contra' : 'En equilibrio'}
                </span>
              )}
            </div>
            <p className={`font-display font-num mt-3 text-2xl font-semibold tracking-tight sm:text-3xl ${
              isPositive ? 'text-positive' : isNegative ? 'text-negative' : 'text-ink'
            }`}>
              {loading
                ? 'S/ …'
                : `${isPositive ? '+ ' : isNegative ? '− ' : ''}${formatCurrency(Math.abs(netBalance))}`}
            </p>
            <p className="font-num mt-1.5 text-xs text-muted">
              {loading
                ? 'Calculando…'
                : prevNetBalance != null
                ? `Mes anterior: ${prevNetBalance > 0 ? '+ ' : prevNetBalance < 0 ? '− ' : ''}${formatCurrency(Math.abs(prevNetBalance))}`
                : 'Diferencia neta entre entradas y gastos'}
            </p>
            {!loading && (
              <div className="font-num mt-4 flex flex-wrap items-center gap-x-4 gap-y-1 border-t border-line/70 pt-3 text-xs text-muted">
                <span>
                  {isPositive
                    ? 'Superávit en el período'
                    : isNegative
                    ? 'Salidas superan a las entradas'
                    : 'Ingresos y gastos igualados'}
                </span>
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
            <button type="button" onClick={onNavigateToTransactions} className="inline-flex items-center gap-1.5 text-sm font-semibold">
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
                const hasCategory = Boolean(transaction.category && transaction.category.trim());
                const categoryColor = hasCategory ? getCategoryColor(transaction.category) : null;
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
                        <span className="flex flex-wrap items-center gap-2">
                          <span className="block truncate text-sm font-semibold text-ink">{transaction.contactName || 'Movimiento'}</span>
                          {hasCategory && categoryColor && (
                            <span
                              className="inline-flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-[10px] font-semibold text-ink"
                              style={{
                                backgroundColor: `${categoryColor}14`,
                                borderColor: `${categoryColor}38`,
                              }}
                            >
                              <span className="h-1.5 w-1.5 shrink-0 rounded-full" style={{ backgroundColor: categoryColor }} aria-hidden="true" />
                              {getCategoryLabel(transaction.category)}
                            </span>
                          )}
                        </span>
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
            <div className="mt-5 space-y-4">
              {analytics.channelBreakdown.slice(0, 5).map((channel, index) => {
                const isWallet = channel.channel.includes('YAPE') || channel.channel.includes('PLIN');
                const Icon = isWallet ? Smartphone : CreditCard;
                return (
                  <button
                    key={`${channel.channel}-${channel.cardLast4 || index}`}
                    type="button"
                    disabled={loading || !onSelectChannel}
                    onClick={() => onSelectChannel?.(channel.channel)}
                    aria-label={`Ver movimientos de ${channel.displayName?.replace(/\*\*/g, '··') || getChannelLabel(channel.channel)}`}
                    className="group block w-full rounded-xl p-2 text-left transition-colors hover:bg-canvas focus-visible:outline-2 focus-visible:outline-brand disabled:cursor-default"
                  >
                    <div className="flex items-start justify-between gap-3">
                      <span className="flex min-w-0 items-center gap-2.5 text-sm font-medium text-ink group-hover:text-brand">
                        <Icon className={`h-4 w-4 shrink-0 ${isWallet ? 'text-yape' : 'text-brand'}`} />
                        <span className="truncate">{channel.displayName?.replace(/\*\*/g, '··') || getChannelLabel(channel.channel)}</span>
                      </span>
                      <span className="font-num shrink-0 text-sm font-semibold">{formatCurrency(channel.amount)}</span>
                    </div>
                    <div className="mt-2.5 h-1.5 overflow-hidden rounded-full bg-canvas group-hover:bg-line/60">
                      <div className={`h-full rounded-full ${isWallet ? 'bg-yape' : 'bg-brand'}`} style={{ width: `${Math.min(100, Math.max(0, channel.percentage))}%` }} />
                    </div>
                    <p className="font-num mt-1 text-right text-xs text-muted">{channel.percentage.toFixed(1)}%</p>
                  </button>
                );
              })}
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
