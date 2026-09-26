import { ArrowDownLeft, ArrowRight, ArrowUpRight, ArrowLeftRight, CreditCard, Smartphone } from 'lucide-react';
import type { PeriodAnalytics, Transaction } from '../../types';
import { formatCurrency, formatRelativeDate, getChannelLabel } from '../../utils';

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
  const netCashflow = income - expense;
  const period = `${MONTHS[selectedMonth - 1]} de ${selectedYear}`;
  const previousExpense = analytics?.previousPeriodExpense;
  const comparison = previousExpense == null
    ? null
    : previousExpense === 0
      ? expense === 0 ? 'Sin gastos en ambos períodos' : 'Primeros gastos frente al período anterior'
      : expense === previousExpense
        ? `Igual que ${analytics?.comparisonThroughDay ? `hasta el día ${analytics.comparisonThroughDay} del mes anterior` : 'el mes anterior'}`
        : `${Math.abs(((expense - previousExpense) / previousExpense) * 100).toFixed(0)}% ${expense > previousExpense ? 'más' : 'menos'} que ${analytics?.comparisonThroughDay ? `hasta el día ${analytics.comparisonThroughDay} del mes anterior` : 'el mes anterior'}`;

  return (
    <div className="space-y-8">
      <div>
        <p className="eyebrow">Resumen del período</p>
        <h1 className="font-display mt-2 text-3xl font-semibold tracking-tight text-ink sm:text-4xl">Así se movió tu dinero</h1>
        <p className="mt-2 text-sm text-muted">Movimientos registrados en {period}. Las transferencias propias no cuentan como gastos.</p>
      </div>

      <section
        className={`surface overflow-hidden${animateIntro && !loading ? ' summary-intro' : ''}`}
        aria-label="Resumen financiero mensual"
        onAnimationEnd={(event) => {
          if (event.target === event.currentTarget) onIntroComplete();
        }}
      >
        <div className="grid lg:grid-cols-[1.15fr_1fr]">
          <div className="border-b border-line p-6 sm:p-8 lg:border-b-0 lg:border-r lg:p-10">
            <p className="eyebrow">Gastos de {period}</p>
            <p className="font-display font-num mt-4 text-[clamp(2.8rem,6vw,5rem)] font-semibold leading-none tracking-tight text-ink">
              {loading ? 'S/ …' : formatCurrency(expense)}
            </p>
            <p className="mt-5 max-w-sm text-sm leading-relaxed text-muted">Total de compras y pagos confirmados durante este mes.</p>
            {!loading && comparison && (
              <p className={`font-num mt-4 text-sm font-semibold ${previousExpense != null && expense > previousExpense ? 'text-negative' : 'text-positive'}`}>
                {comparison}
              </p>
            )}
          </div>
          <div className="grid grid-cols-2 divide-x divide-line lg:grid-cols-1 lg:divide-x-0 lg:divide-y">
            <div className="p-5 sm:p-7">
              <div className="flex items-center gap-2 text-positive"><ArrowDownLeft className="h-4 w-4" /><span className="eyebrow !text-positive">Entradas</span></div>
              <p className="font-display font-num mt-3 text-xl font-semibold tracking-tight sm:text-2xl">{loading ? 'S/ …' : formatCurrency(income)}</p>
            </div>
            <div className="p-5 sm:p-7">
              <div className="flex items-center gap-2 text-brand"><ArrowLeftRight className="h-4 w-4" /><span className="eyebrow !text-brand">Flujo neto</span></div>
              <p className={`font-display font-num mt-3 text-xl font-semibold tracking-tight sm:text-2xl ${netCashflow < 0 ? 'text-negative' : 'text-ink'}`}>
                {loading ? 'S/ …' : formatCurrency(netCashflow)}
              </p>
            </div>
          </div>
        </div>
        <div className="h-1.5 w-full bg-brand" aria-hidden="true" />
      </section>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.7fr)_minmax(300px,1fr)]">
        <section className="surface min-w-0" aria-labelledby="recent-heading">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line px-6 py-5">
            <div>
              <h2 id="recent-heading" className="font-display text-lg font-semibold">Movimientos recientes</h2>
              <p className="mt-1 text-sm text-muted">Los últimos movimientos registrados en el período.</p>
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
            <p className="mt-1 text-sm text-muted">Participación en tus gastos del mes.</p>
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
          {(analytics?.internalTransfersAmount ?? 0) > 0 && (
            <p className="mt-6 border-t border-line pt-4 text-xs leading-relaxed text-muted">
              {formatCurrency(analytics?.internalTransfersAmount ?? 0)} en transferencias propias, excluidas de gastos.
            </p>
          )}
        </section>
      </div>
    </div>
  );
}
