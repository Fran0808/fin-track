import { RefreshCw } from 'lucide-react';
import { useQueryResource } from '../../hooks/useQueryResource';
import { api } from '../../services/api';

export function PhoneReceptionPanel() {
  const { data, loading, error, refresh } = useQueryResource(api.getPhoneReceptionStatus, 'No se pudo consultar la recepción del teléfono.');
  const date = data?.lastReceivedAt ? new Date(data.lastReceivedAt) : null;
  return (
    <div className="mt-6 rounded-2xl border border-line bg-canvas p-4">
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm font-semibold text-ink">Última recepción</p>
        <button
          type="button"
          aria-label={error ? 'Reintentar recepción' : 'Actualizar estado'}
          title={error ? 'Reintentar recepción' : 'Actualizar estado'}
          disabled={loading}
          onClick={() => void refresh()}
          className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-xl text-brand hover:bg-brand/8 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand disabled:opacity-50"
        >
          <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin motion-reduce:animate-none' : ''}`} aria-hidden="true" />
        </button>
      </div>
      <p role="status" className="mt-2 text-sm text-muted">
        {loading ? 'Consultando recepción...' : error ? 'No se pudo consultar la recepción del teléfono.' : date
          ? new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short' }).format(date)
          : 'Sin movimientos recibidos'}
      </p>
      <p className="mt-3 text-sm leading-6 text-muted">Si no llegan movimientos, revisa en el teléfono la vinculación, el acceso a notificaciones y la conexión con el servidor.</p>
    </div>
  );
}
