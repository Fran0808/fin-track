import { useCallback } from 'react';
import { api } from '../../services/api';
import { useQueryResource } from '../../hooks/useQueryResource';
import type { Transaction } from '../../types';
import { formatDate } from '../../utils';

export function TransactionOriginPanel({ transactionId, captureOrigin, createdAt }: {
  transactionId: number;
  captureOrigin?: Transaction['captureOrigin'];
  createdAt?: string;
}) {
  const load = useCallback(() => api.getTransactionOrigin(transactionId), [transactionId]);
  const { data, loading, error, refresh } = useQueryResource(load, 'No se pudo consultar el origen.');
  const origin = data?.origin ?? captureOrigin;
  const label = origin === 'EMAIL' ? 'Correo' : origin === 'PHONE' ? 'Teléfono' : origin === 'MANUAL' ? 'Manual' : null;
  const reference = data?.origin === 'EMAIL' ? data.operationReference : null;
  const registeredAt = data ? data.registeredAt : createdAt;

  return (
    <>
      <div>
        <dt className="text-xs text-muted">Origen</dt>
        <dd className="mt-0.5 font-medium">
          {label ?? (loading && !origin ? 'Consultando origen...' : 'Origen no disponible')}
        </dd>
      </div>
      <div>
        <dt className="text-xs text-muted">{reference ? 'Referencia' : 'Registro'}</dt>
        <dd className="mt-0.5 min-w-0 break-all font-medium">
          {reference || (registeredAt ? formatDate(registeredAt) : loading ? '—' : 'No disponible')}
        </dd>
      </div>
      {error && <div className="col-span-2">
        <dt className="sr-only">Consulta del origen</dt>
        <dd role="status" className="text-xs text-muted">
          No se pudo consultar el origen.{' '}
          <button type="button" onClick={() => void refresh()} className="font-semibold text-brand underline">
            Reintentar origen
          </button>
        </dd>
      </div>}
    </>
  );
}
