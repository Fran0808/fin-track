import { useEffect, useState } from 'react';
import { Check, ChevronLeft, ChevronRight, RefreshCw } from 'lucide-react';
import type { PageResponse, PendingReason, Transaction } from '../../types';
import { api } from '../../services/api';
import { formatCurrency, formatDate } from '../../utils';

const REASONS: { value: PendingReason; label: string }[] = [
  { value: 'ALL', label: 'Todos los pendientes' },
  { value: 'CATEGORY', label: 'Sin categoría' },
  { value: 'INSTRUMENT', label: 'Sin medio asignado' },
];

export function PendingView({ revision, onSelectTransaction, onOpenRules }: {
  revision: number;
  onSelectTransaction: (transaction: Transaction) => void;
  onOpenRules: () => void;
}) {
  const [reason, setReason] = useState<PendingReason>('ALL');
  const [page, setPage] = useState(0);
  const [retry, setRetry] = useState(0);
  const [resource, setResource] = useState<{ key: string; data: PageResponse<Transaction> | null; error: string | null } | null>(null);
  const key = `${reason}:${page}:${revision}:${retry}`;
  const loading = resource?.key !== key;
  const data = loading ? null : resource?.data;
  const error = loading ? null : resource?.error;

  useEffect(() => {
    let cancelled = false;
    api.getPendingTransactions(reason, page).then(result => {
      if (cancelled) return;
      if (page > 0 && page >= result.totalPages) {
        setPage(Math.max(0, result.totalPages - 1));
        return;
      }
      setResource({ key, data: result, error: null });
    }).catch(() => {
      if (!cancelled) setResource({ key, data: null, error: 'No se pudieron cargar los pendientes. Inténtalo de nuevo.' });
    });
    return () => { cancelled = true; };
  }, [reason, page, key]);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="eyebrow">Organiza tu historial</p>
          <h1 className="font-display mt-2 text-3xl font-semibold tracking-tight sm:text-4xl">Pendientes</h1>
          <p className="mt-2 max-w-2xl text-sm text-muted">Completa la categoría o el medio de pago de tus movimientos. Incluye todo el historial, sin limitarse al mes seleccionado.</p>
        </div>
        <button type="button" onClick={onOpenRules} className="rounded-xl border border-line bg-white px-4 py-2.5 text-sm font-semibold text-brand hover:bg-brand/5">Configurar reglas</button>
      </div>

      <section className="surface overflow-hidden" aria-label="Bandeja de pendientes">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line p-5 sm:px-6">
          <div className="flex flex-wrap gap-2" aria-label="Filtrar pendientes">
            {REASONS.map(item => <button key={item.value} type="button" aria-pressed={reason === item.value}
              onClick={() => { setReason(item.value); setPage(0); }}
              className={`rounded-xl px-3 py-2 text-sm font-medium ${reason === item.value ? 'bg-brand text-white' : 'bg-canvas text-muted hover:text-ink'}`}>{item.label}</button>)}
          </div>
          <button type="button" disabled={loading} onClick={() => setRetry(value => value + 1)} aria-label="Actualizar pendientes" className="rounded-lg p-2 text-brand hover:bg-canvas disabled:opacity-40"><RefreshCw className="h-4 w-4" /></button>
        </div>
        {loading && <p role="status" className="p-6 text-sm text-muted">Cargando pendientes...</p>}
        {error && <div className="p-6"><p role="alert" className="text-sm text-negative">{error}</p><button type="button" onClick={() => setRetry(value => value + 1)} className="mt-3 text-sm font-semibold text-brand">Reintentar</button></div>}
        {data && <>
          <p className="border-b border-line px-6 py-3 text-sm text-muted">{data.totalElements} {data.totalElements === 1 ? 'movimiento' : 'movimientos'} por completar</p>
          {data.content.length === 0 ? <div className="px-6 py-12 text-center">
            <Check className="mx-auto h-7 w-7 text-positive" aria-hidden="true" />
            <h2 className="font-display mt-3 text-xl font-semibold">Todo al día</h2>
            <p className="mt-2 text-sm text-muted">No hay movimientos pendientes con este filtro.</p>
          </div> : <ul className="divide-y divide-line">
            {data.content.map(transaction => <li key={transaction.id} className="flex flex-wrap items-center justify-between gap-4 px-5 py-5 sm:px-6">
              <div className="min-w-0 flex-1 basis-48">
                <p className="break-words font-semibold">{transaction.contactName}</p>
                <p className="mt-1 text-xs text-muted">{formatDate(transaction.transactionDate)} · {transaction.flowType === 'INCOME' ? 'Entrada' : transaction.flowType === 'INTERNAL_TRANSFER' ? 'Transferencia propia' : 'Gasto'}</p>
                <div className="mt-2 flex flex-wrap gap-2 text-xs">
                  {transaction.flowType !== 'INTERNAL_TRANSFER' && !transaction.category?.trim() && <span className="rounded-md bg-brand/8 px-2 py-1 text-brand">Sin categoría</span>}
                  {!transaction.financialInstrument && <span className="rounded-md bg-canvas px-2 py-1 text-muted">Sin medio asignado</span>}
                </div>
              </div>
              <div className="flex items-center gap-4">
                <span className="font-num whitespace-nowrap text-sm font-semibold">{formatCurrency(transaction.amount)}</span>
                <button type="button" onClick={() => onSelectTransaction(transaction)} aria-label={`Completar ${transaction.contactName}`} className="rounded-xl border border-line px-3 py-2 text-sm font-semibold text-brand hover:bg-brand/5">Completar</button>
              </div>
            </li>)}
          </ul>}
          {data.totalPages > 1 && <nav aria-label="Paginación de pendientes" className="flex items-center justify-between border-t border-line px-6 py-4">
            <span className="text-sm text-muted">Página {data.number + 1} de {data.totalPages}</span>
            <div className="flex gap-2">
              <button type="button" disabled={data.first} onClick={() => setPage(page - 1)} aria-label="Pendientes anteriores" className="rounded-lg border border-line p-2 disabled:opacity-40"><ChevronLeft className="h-4 w-4" /></button>
              <button type="button" disabled={data.last} onClick={() => setPage(page + 1)} aria-label="Pendientes siguientes" className="rounded-lg border border-line p-2 disabled:opacity-40"><ChevronRight className="h-4 w-4" /></button>
            </div>
          </nav>}
        </>}
      </section>
    </div>
  );
}
