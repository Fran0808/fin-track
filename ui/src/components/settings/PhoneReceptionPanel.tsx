import { RefreshCw } from 'lucide-react';
import { useQueryResource } from '../../hooks/useQueryResource';
import { api } from '../../services/api';

export function PhoneReceptionPanel() {
  const { data, loading, error, refresh } = useQueryResource(api.getPhoneReceptionStatus, 'No se pudo consultar la recepción del teléfono.');
  const date = data?.lastReceivedAt ? new Date(data.lastReceivedAt) : null;
  return (
    <div className="mt-5 space-y-4">
      <div className="flex items-center justify-between gap-4 rounded-xl border border-line bg-canvas/50 px-4 py-3 text-sm">
        <div className="min-w-0">
          <p className="text-muted">Última recepción</p>
          <p role="status" className="font-num mt-0.5 font-medium text-ink">
            {loading ? 'Consultando recepción...' : error ? 'No se pudo consultar la recepción del teléfono.' : date
              ? new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short' }).format(date)
              : 'Sin movimientos recibidos'}
          </p>
        </div>
        <button
          type="button"
          aria-label={error ? 'Reintentar recepción' : 'Actualizar estado'}
          title={error ? 'Reintentar recepción' : 'Actualizar estado'}
          disabled={loading}
          onClick={() => void refresh()}
          className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-muted transition-colors hover:bg-canvas hover:text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand disabled:opacity-50"
        >
          <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin motion-reduce:animate-none' : ''}`} aria-hidden="true" />
        </button>
      </div>
      <p className="text-xs leading-5 text-muted">
        Si no llegan movimientos, revisa en el teléfono la vinculación, el acceso a notificaciones y la conexión con el servidor.
      </p>
    </div>
  );
}
