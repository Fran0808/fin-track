import { ArrowDownLeft, ArrowLeftRight, ArrowUpRight, ChevronLeft, ChevronRight } from 'lucide-react';
import type { Transaction, TransactionPageResponse, FlowType } from '../../types';
import { STANDARD_CATEGORIES } from '../../types';
import { formatCurrency, formatDate, getChannelLabel, getTransactionFlowLabel } from '../../utils';

interface TransactionTableProps {
  pageData: TransactionPageResponse | null;
  flowType?: FlowType | '';
  loading: boolean;
  periodName?: string;
  onPageChange: (page: number) => void;
  onSelectTransaction: (transaction: Transaction) => void;
}

function getCategoryLabel(categoryId?: string | null): string | null {
  if (!categoryId) return null;
  const found = STANDARD_CATEGORIES.find((c) => c.id === categoryId);
  return found ? found.label : categoryId;
}

export function TransactionTable({ pageData, flowType, loading, periodName, onPageChange, onSelectTransaction }: TransactionTableProps) {
  const metrics = [
    { flow: 'EXPENSE', key: 'totalExpense', label: 'Gastos' },
    { flow: 'INCOME', key: 'totalIncome', label: 'Entradas' },
    { flow: 'INTERNAL_TRANSFER', key: 'internalTransfersAmount', label: 'Transferencias internas' },
  ] as const;
  const visibleMetrics = metrics.filter(metric => !flowType || metric.flow === flowType);

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line px-5 py-4 sm:px-6">
        <div>
          <h2 className="font-display text-lg font-semibold">
            Registro {periodName && <span className="ml-1 text-sm font-normal text-muted">· {periodName}</span>}
          </h2>
          <p className="mt-1 text-sm text-muted">
            {pageData ? `${pageData.totalElements} movimientos encontrados` : 'Cargando movimientos'}
          </p>
        </div>

      </div>

      {pageData && (
        <div className="border-b border-line bg-canvas/50 px-5 py-4 sm:px-6" aria-busy={loading}>
          {pageData.summary ? (
            <div role="group" aria-label="Total del filtro">
              <p className="text-xs font-semibold text-ink">Total del filtro · {pageData.totalElements} movimientos</p>
              <dl className="mt-2 flex flex-wrap gap-x-6 gap-y-2">
                {visibleMetrics.map(metric => (
                  <div key={metric.key} className="text-sm">
                    <dt className="text-xs text-muted">{metric.label}</dt>
                    <dd className="font-num font-semibold text-ink">{formatCurrency(pageData.summary![metric.key])}</dd>
                  </div>
                ))}
              </dl>
            </div>
          ) : <p role="status" className="text-sm text-muted">Total del filtro no disponible. Actualiza el backend para consultarlo.</p>}
          <div role="group" aria-label="Esta página" className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted">
            <span className="font-semibold">Esta página · {pageData.content.length} movimientos</span>
            {visibleMetrics.map(metric => {
              const cents = pageData.content.filter(t => t.flowType === metric.flow)
                .reduce((total, t) => total + Math.round(t.amount * 100), 0);
              return <span key={metric.key}>{metric.label}: <strong className="font-num font-semibold text-ink">{formatCurrency(cents / 100)}</strong></span>;
            })}
          </div>
          {loading && <p role="status" className="mt-2 text-xs font-semibold text-brand">Actualizando...</p>}
        </div>
      )}

      <div className="overflow-x-auto">
        <table className="w-full table-fixed border-collapse text-left sm:min-w-[700px] sm:table-auto">
          <caption className="sr-only">Movimientos del período seleccionado</caption>
          <thead>
            <tr className="border-y border-line bg-canvas/70 text-xs font-semibold uppercase tracking-wider text-muted">
              <th scope="col" className="px-3 py-3 sm:px-6">Comercio o contacto</th>
              <th scope="col" className="hidden px-4 py-3 sm:table-cell">Fecha</th>
              <th scope="col" className="hidden px-4 py-3 sm:table-cell">Medio</th>
              <th scope="col" className="w-28 px-2 py-3 text-right sm:w-auto sm:px-4">Importe</th>
              <th scope="col" className="w-12 px-1 py-3 text-right sm:w-auto sm:px-6"><span className="sr-only">Detalle</span></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {loading && !pageData ? (
              <tr><td colSpan={5} className="px-6 py-12 text-center text-sm text-muted">Cargando movimientos...</td></tr>
            ) : !pageData?.content.length ? (
              <tr><td colSpan={5} className="px-6 py-12 text-center text-sm text-muted">No se encontraron movimientos. Prueba otro período o ajusta los filtros.</td></tr>
            ) : pageData.content.map((transaction) => {
              const isIncome = transaction.flowType === 'INCOME';
              const isTransfer = transaction.flowType === 'INTERNAL_TRANSFER';
              const Icon = isIncome ? ArrowDownLeft : isTransfer ? ArrowLeftRight : ArrowUpRight;
              const categoryLabel = getCategoryLabel(transaction.category);

              return (
                <tr key={transaction.id} className="transition-colors hover:bg-canvas/80">
                  <td className="px-3 py-4 sm:px-6">
                    <div className="flex items-start gap-3">
                      <span className={`mt-0.5 hidden h-9 w-9 shrink-0 items-center justify-center rounded-lg sm:flex ${isIncome ? 'bg-positive/10 text-positive' : isTransfer ? 'bg-brand/10 text-brand' : 'bg-negative/10 text-negative'}`}>
                        <Icon className="h-4 w-4" />
                      </span>
                      <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-1.5">
                          <span className="block max-w-full truncate text-sm font-semibold text-ink sm:max-w-56">{transaction.contactName || 'Movimiento'}</span>
                          {categoryLabel && (
                            <span className="inline-flex items-center rounded-full bg-brand/10 px-2 py-0.5 text-[10px] font-semibold text-brand">
                              {categoryLabel}
                            </span>
                          )}
                        </div>
                        <div className="mt-1 flex flex-wrap items-center gap-1.5 text-xs text-muted">
                          <span>{getTransactionFlowLabel(transaction)}</span>
                        </div>
                      </div>
                    </div>
                  </td>
                  <td className="hidden whitespace-nowrap px-4 py-4 text-sm text-muted sm:table-cell">{formatDate(transaction.transactionDate)}</td>
                  <td className="hidden whitespace-nowrap px-4 py-4 text-sm text-muted sm:table-cell">{getChannelLabel(transaction.channel)}{transaction.cardLast4 ? ` ··${transaction.cardLast4}` : ''}</td>
                  <td className={`font-num break-words px-2 py-4 text-right text-xs font-semibold sm:whitespace-nowrap sm:px-4 sm:text-sm ${isIncome ? 'text-positive' : isTransfer ? 'text-muted' : 'text-ink'}`}>
                    {isIncome ? '+ ' : isTransfer ? '' : '− '}{formatCurrency(transaction.amount)}
                  </td>
                  <td className="px-1 py-4 text-right sm:px-6">
                    <button type="button" onClick={() => onSelectTransaction(transaction)} className="rounded-lg px-1 py-2 text-sm font-semibold text-brand hover:bg-brand/10 sm:px-2.5" aria-label={`Ver detalle de ${transaction.contactName || 'movimiento'}`}>
                      Ver
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {pageData && pageData.totalPages > 1 && (
        <nav aria-label="Paginación de movimientos" className="flex items-center justify-between gap-3 border-t border-line px-5 py-4 sm:px-6">
          <span className="text-sm text-muted">Página {pageData.number + 1} de {pageData.totalPages}</span>
          <div className="flex gap-2">
            <button type="button" onClick={() => onPageChange(pageData.number - 1)} disabled={pageData.first || loading} className="rounded-lg border border-line p-2 text-ink hover:bg-canvas disabled:opacity-40" aria-label="Página anterior"><ChevronLeft className="h-4 w-4" /></button>
            <button type="button" onClick={() => onPageChange(pageData.number + 1)} disabled={pageData.last || loading} className="rounded-lg border border-line p-2 text-ink hover:bg-canvas disabled:opacity-40" aria-label="Página siguiente"><ChevronRight className="h-4 w-4" /></button>
          </div>
        </nav>
      )}
    </div>
  );
}
