import { useCallback } from 'react';
import { api } from '../../services/api';
import { useQueryResource } from '../../hooks/useQueryResource';
import { formatDate } from '../../utils';

export function TransactionOriginPanel({ transactionId }: { transactionId: number }) {
  const load = useCallback(() => api.getTransactionOrigin(transactionId), [transactionId]);
  const { data, loading, error, refresh } = useQueryResource(load, 'No se pudo consultar el origen.');
  const label = data?.origin === 'EMAIL' ? 'Correo' : data?.origin === 'PHONE' ? 'Teléfono' : data?.origin === 'MANUAL' ? 'Manual' : 'Origen no disponible';

  return <section aria-labelledby="transaction-origin-title" className="mt-6 rounded-2xl border border-line bg-canvas/40 p-4">
    <h3 id="transaction-origin-title" className="text-sm font-semibold">Origen del movimiento</h3>
    <p className="mt-1 text-xs text-muted">{data?.origin === 'MANUAL' ? 'Registrado manualmente en FinTrack.' : 'Primera captura registrada en FinTrack.'}</p>
    {loading && <p role="status" className="mt-3 text-sm text-muted">Consultando origen...</p>}
    {error && <div className="mt-3 text-sm"><p role="status" className="text-muted">No se pudo consultar el origen.</p>
      <button type="button" onClick={() => void refresh()} className="mt-2 font-semibold text-brand">Reintentar origen</button></div>}
    {!loading && !error && data && <>
      <dl className="mt-3 divide-y divide-line text-sm">
        <div className="flex justify-between gap-4 py-2.5"><dt className="text-muted">{data.origin === 'MANUAL' ? 'Registrado por' : 'Capturado por'}</dt><dd className="text-right font-medium">{label}</dd></div>
        <div className="py-2.5 sm:flex sm:justify-between sm:gap-4"><dt className="text-muted">Registrado en FinTrack</dt><dd className="mt-1 font-medium sm:mt-0 sm:text-right">{data.registeredAt ? formatDate(data.registeredAt) : 'No disponible'}</dd></div>
        <div className="flex justify-between gap-4 py-2.5"><dt className="text-muted">Referencia de operación</dt>
          <dd className="min-w-0 break-all text-right font-medium">{data.origin === 'EMAIL' && data.operationReference ? data.operationReference : 'No registrada'}</dd></div>
      </dl>
      {data.origin === 'UNKNOWN' && <p className="mt-2 text-xs text-muted">Esta captura no conserva información que permita identificar su origen.</p>}
    </>}
  </section>;
}
