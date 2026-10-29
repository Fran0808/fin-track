import { useCallback } from 'react';
import { api } from '../../services/api';
import { useQueryResource } from '../../hooks/useQueryResource';
import { formatDate } from '../../utils';

export function TransactionOriginPanel({ transactionId }: { transactionId: number }) {
  const load = useCallback(() => api.getTransactionOrigin(transactionId), [transactionId]);
  const { data, loading, error, refresh } = useQueryResource(load, 'No se pudo consultar el origen.');
  const label = data?.origin === 'EMAIL' ? 'Correo' : data?.origin === 'PHONE' ? 'Teléfono' : data?.origin === 'MANUAL' ? 'Manual' : 'Origen no disponible';
  const hasEmailReference = Boolean(data?.origin === 'EMAIL' && data.operationReference);

  if (loading) {
    return (
      <div className="col-span-2">
        <dt className="text-xs text-muted">Origen</dt>
        <dd role="status" className="mt-0.5 text-sm text-muted">Consultando origen...</dd>
      </div>
    );
  }

  if (error) {
    return (
      <div className="col-span-2 flex items-center justify-between gap-3">
        <div>
          <dt className="text-xs text-muted">Origen</dt>
          <dd role="status" className="mt-0.5 text-sm text-muted">No se pudo consultar el origen.</dd>
        </div>
        <button type="button" onClick={() => void refresh()} className="shrink-0 text-xs font-semibold text-brand hover:underline">
          Reintentar origen
        </button>
      </div>
    );
  }

  if (!data) return null;

  return (
    <>
      <div>
        <dt className="text-xs text-muted">Origen</dt>
        <dd className="mt-0.5 font-medium">{label}</dd>
      </div>
      <div>
        <dt className="text-xs text-muted">{hasEmailReference ? 'Referencia' : 'Registro'}</dt>
        <dd className="mt-0.5 min-w-0 break-all font-medium">
          {hasEmailReference ? data.operationReference : data.registeredAt ? formatDate(data.registeredAt) : 'No disponible'}
        </dd>
      </div>
    </>
  );
}
