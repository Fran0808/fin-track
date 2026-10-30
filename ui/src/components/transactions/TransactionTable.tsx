import { ArrowDownLeft, ArrowLeftRight, ArrowUpRight, ChevronLeft, ChevronRight, Pencil } from 'lucide-react';
import type { Transaction, TransactionPageResponse, FlowType } from '../../types';
import { formatCurrency, formatTransactionDateParts, getChannelLabel, getTransactionFlowLabel } from '../../utils';
import { getCategoryColor, getCategoryLabel } from '../../utils/categoryColors';

interface TransactionTableProps {
  selectedTransactionId?: number;
  selectionDisabled?: boolean;
  pageData: TransactionPageResponse | null;
  flowType?: FlowType | '';
  loading: boolean;
  periodName?: string;
  onPageChange: (page: number) => void;
  onSelectTransaction: (transaction: Transaction, options?: { edit?: boolean }) => void;
}

function CategoryChip({ category, isTransfer, compact = false }: { category?: string | null; isTransfer: boolean; compact?: boolean }) {
  const hasCategory = Boolean(category && category.trim());
  if (hasCategory) {
    const label = getCategoryLabel(category);
    const color = getCategoryColor(category);
    return (
      <span
        className={`inline-flex items-center gap-1.5 rounded-full border font-semibold text-ink ${
          compact ? 'px-2 py-0.5 text-[10px] @4xl:hidden' : 'px-2.5 py-0.5 text-xs'
        }`}
        style={{
          backgroundColor: `${color}14`,
          borderColor: `${color}38`,
        }}
      >
        <span
          className="h-2 w-2 shrink-0 rounded-full"
          style={{ backgroundColor: color }}
          aria-hidden="true"
        />
        {label}
      </span>
    );
  }

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full border font-medium ${
        compact ? 'px-2 py-0.5 text-[10px] @4xl:hidden' : 'px-2.5 py-0.5 text-xs'
      } ${
        isTransfer
          ? 'border-line bg-canvas text-muted'
          : 'border-dashed border-amber-500/55 bg-amber-500/10 text-amber-900'
      }`}
    >
      {!isTransfer && <span className="h-1.5 w-1.5 shrink-0 rounded-full bg-amber-500" aria-hidden="true" />}
      Sin categoría
    </span>
  );
}

export function TransactionTable({ pageData, flowType, loading, periodName, onPageChange, onSelectTransaction, selectedTransactionId, selectionDisabled = false }: TransactionTableProps) {
  const metrics = [
    { flow: 'EXPENSE', key: 'totalExpense', label: 'Gastos' },
    { flow: 'INCOME', key: 'totalIncome', label: 'Entradas' },
    { flow: 'INTERNAL_TRANSFER', key: 'internalTransfersAmount', label: 'Transferencias propias' },
  ] as const;
  const visibleMetrics = metrics.filter(metric => !flowType || metric.flow === flowType);

  const month = periodName?.match(/^(\d{1,2})\/(\d{4})$/);
  const scope = month ? new Date(Number(month[2]), Number(month[1]) - 1, 1).toLocaleDateString('es-PE', { month: 'long', year: 'numeric' }) : periodName;

  return (
    <div className="@container">
      <div className="flex flex-col gap-4 border-b border-line px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:px-6" aria-busy={loading}>
        <div className="shrink-0">
          <h2 className="font-display text-base font-semibold">{pageData ? `${pageData.totalElements} ${pageData.totalElements === 1 ? 'movimiento' : 'movimientos'}` : 'Cargando movimientos'}</h2>
          {scope && <p className="mt-1 text-xs text-muted">{scope}</p>}
          {loading && pageData && <p role="status" className="mt-1 text-xs text-brand">Actualizando...</p>}
        </div>
        {pageData && (pageData.summary ? (
          <div role="group" aria-label="Total del filtro">
            <p className="sr-only">Totales de todos los resultados de la búsqueda, no solo de esta página.</p>
            <dl className="grid grid-cols-3 gap-x-3 gap-y-3 sm:flex sm:flex-wrap sm:justify-end sm:gap-x-8">
              {visibleMetrics.map(metric => (
                <div key={metric.key} className="min-w-0">
                  <dt className="min-h-8 text-xs text-muted sm:min-h-0">{metric.label}</dt>
                  <dd className={`font-num mt-0.5 break-words text-base font-semibold sm:text-lg ${metric.flow === 'INCOME' ? 'text-positive' : 'text-ink'}`}>{formatCurrency(pageData.summary![metric.key])}</dd>
                </div>
              ))}
            </dl>
          </div>
        ) : <p role="status" className="text-sm text-muted">Total del filtro no disponible. Actualiza el backend para consultarlo.</p>)}
      </div>

      <div className="overflow-x-auto">
        <table className="w-full table-fixed border-collapse text-left @2xl:table-auto">
          <caption className="sr-only">Movimientos del período seleccionado</caption>
          <thead>
            <tr className="border-y border-line bg-canvas/70 text-xs font-semibold uppercase tracking-wider text-muted">
              <th scope="col" className="px-3 py-3 sm:px-6">Comercio o contacto</th>
              <th scope="col" className="hidden px-4 py-3 @4xl:table-cell">Categoría</th>
              <th scope="col" className="hidden px-4 py-3 @2xl:table-cell">Fecha</th>
              <th scope="col" className="hidden px-4 py-3 @2xl:table-cell">Medio</th>
              <th scope="col" className="w-28 px-2 py-3 text-right @2xl:w-auto sm:px-4">Importe</th>
              <th scope="col" className="w-12 px-1 py-3 text-right @2xl:w-auto sm:px-6"><span className="sr-only">Detalle</span></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line/60">
            {loading && !pageData ? (
              <tr><td colSpan={6} className="px-6 py-12 text-center text-sm text-muted">Cargando movimientos...</td></tr>
            ) : !pageData?.content.length ? (
              <tr><td colSpan={6} className="px-6 py-12 text-center text-sm text-muted">No se encontraron movimientos. Prueba otro período o ajusta los filtros.</td></tr>
            ) : pageData.content.map((transaction) => {
              const isIncome = transaction.flowType === 'INCOME';
              const isTransfer = transaction.flowType === 'INTERNAL_TRANSFER';
              const Icon = isIncome ? ArrowDownLeft : isTransfer ? ArrowLeftRight : ArrowUpRight;
              const dateParts = formatTransactionDateParts(transaction.transactionDate);

              return (
                <tr key={transaction.id} aria-selected={selectedTransactionId === transaction.id}
                  onClick={() => { if (!selectionDisabled) onSelectTransaction(transaction, { edit: false }); }}
                  className={`transition-colors ${selectionDisabled ? 'cursor-wait' : 'cursor-pointer'} ${selectedTransactionId === transaction.id ? 'bg-brand/5 shadow-[inset_3px_0_0_var(--color-brand)]' : 'hover:bg-canvas/80'}`}>
                  <td className="px-3 py-3 sm:px-6">
                    <div className="flex items-start gap-3">
                      <span className={`mt-0.5 hidden h-8 w-8 shrink-0 items-center justify-center rounded-lg sm:flex ${isIncome ? 'bg-positive/10 text-positive' : isTransfer ? 'bg-brand/10 text-brand' : 'bg-negative/10 text-negative'}`}>
                        <Icon className="h-4 w-4" />
                      </span>
                      <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-1.5">
                          <span className="block max-w-full truncate text-sm font-semibold text-ink sm:max-w-56">{transaction.contactName || 'Movimiento'}</span>
                          {transaction.captureOrigin === 'MANUAL' && <span className="rounded-full border border-line px-2 py-0.5 text-[10px] font-semibold text-muted">Manual</span>}
                          <CategoryChip category={transaction.category} isTransfer={isTransfer} compact />
                        </div>
                        <div className="mt-1 flex flex-wrap items-center gap-1.5 text-xs text-muted">
                          <span>{getTransactionFlowLabel(transaction)}</span>
                          <span className="@2xl:hidden">· {dateParts.date}</span>
                        </div>
                      </div>
                    </div>
                  </td>
                  <td className="hidden px-4 py-3 @4xl:table-cell">
                    <CategoryChip category={transaction.category} isTransfer={isTransfer} />
                  </td>
                  <td className="hidden whitespace-nowrap px-4 py-3 text-sm text-muted @2xl:table-cell"><span className="block">{dateParts.date}</span><span className="mt-0.5 block text-xs">{dateParts.time}</span></td>
                  <td className="hidden whitespace-nowrap px-4 py-3 text-sm text-muted @2xl:table-cell">{getChannelLabel(transaction.channel)}{transaction.cardLast4 ? ` ··${transaction.cardLast4}` : ''}</td>
                  <td className={`font-num break-words px-2 py-3 text-right text-xs font-semibold @2xl:whitespace-nowrap sm:px-4 sm:text-sm ${isIncome ? 'text-positive' : isTransfer ? 'text-muted' : 'text-ink'}`}>
                    {isIncome ? '+ ' : isTransfer ? '' : '− '}{formatCurrency(transaction.amount)}
                  </td>
                  <td className="px-1 py-3 text-right sm:px-6">
                    <div className="flex items-center justify-end gap-1">
                      {transaction.reconciledToId == null && (
                        <button
                          type="button"
                          disabled={selectionDisabled}
                          onClick={event => {
                            event.stopPropagation();
                            onSelectTransaction(transaction, { edit: true });
                          }}
                          className="rounded-lg p-1.5 text-muted transition-colors hover:bg-canvas hover:text-brand focus-visible:outline-2 focus-visible:outline-brand disabled:opacity-50"
                          aria-label={`Editar ${transaction.contactName || 'movimiento'}`}
                          title="Editar movimiento"
                        >
                          <Pencil className="h-4 w-4" />
                        </button>
                      )}
                      <button
                        type="button"
                        disabled={selectionDisabled}
                        onClick={event => {
                          event.stopPropagation();
                          onSelectTransaction(transaction, { edit: false });
                        }}
                        aria-current={selectedTransactionId === transaction.id ? 'true' : undefined}
                        className="rounded-lg p-1.5 text-brand transition-colors hover:bg-canvas focus-visible:outline-2 focus-visible:outline-brand disabled:opacity-50"
                        aria-label={`Ver detalle de ${transaction.contactName || 'movimiento'}`}
                        title="Ver detalle"
                      >
                        <ChevronRight className="h-4 w-4" />
                      </button>
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {pageData && <div className="flex flex-wrap items-start justify-between gap-3 border-t border-line px-5 py-3 sm:px-6">
          <div role="group" aria-label="Esta página" className="flex flex-wrap gap-x-4 gap-y-1 py-1 text-xs text-muted">
            <span>{pageData.content.length} {pageData.content.length === 1 ? 'movimiento' : 'movimientos'} en esta página</span>
            {visibleMetrics.map(metric => {
              const cents = pageData.content.filter(t => t.flowType === metric.flow).reduce((total, t) => total + Math.round(t.amount * 100), 0);
              return <span key={metric.key}>{metric.label}: <strong className="font-num font-semibold text-ink">{formatCurrency(cents / 100)}</strong></span>;
            })}
          </div>
        {pageData.totalPages > 1 && <nav aria-label="Paginación de movimientos" className="flex items-center gap-3">
          <span className="text-xs text-muted">{pageData.number * pageData.size + 1}–{pageData.number * pageData.size + pageData.content.length} de {pageData.totalElements}</span>
          <div className="flex gap-2">
            <button type="button" onClick={() => onPageChange(pageData.number - 1)} disabled={pageData.number === 0 || loading} className="rounded-lg border border-line p-2 text-ink hover:bg-canvas disabled:opacity-40" aria-label="Página anterior"><ChevronLeft className="h-4 w-4" /></button>
            <button type="button" onClick={() => onPageChange(pageData.number + 1)} disabled={pageData.number + 1 >= pageData.totalPages || loading} className="rounded-lg border border-line p-2 text-ink hover:bg-canvas disabled:opacity-40" aria-label="Página siguiente"><ChevronRight className="h-4 w-4" /></button>
          </div>
        </nav>}
      </div>}

    </div>
  );
}
