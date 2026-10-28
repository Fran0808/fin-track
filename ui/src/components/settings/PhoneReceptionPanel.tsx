import { useQueryResource } from '../../hooks/useQueryResource';
import { api } from '../../services/api';

export function PhoneReceptionPanel() {
  const { data, loading, error, refresh } = useQueryResource(api.getPhoneReceptionStatus, 'No se pudo consultar la recepción del teléfono.');
  const date = data?.lastReceivedAt ? new Date(data.lastReceivedAt) : null;
  return (
    <div className="mt-6 rounded-2xl border border-line bg-canvas p-4">
      <p className="text-sm font-semibold text-ink">Última recepción del teléfono</p>
      <p role="status" className="mt-2 text-sm text-muted">
        {loading ? 'Consultando recepción...' : error ? 'No se pudo consultar la recepción del teléfono.' : date
          ? new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short' }).format(date)
          : 'Sin recepciones registradas desde que se habilitó este seguimiento.'}
      </p>
      <p className="mt-3 text-xs leading-5 text-muted">
        Una recepción confirma que FinTrack aceptó un envío de movimientos, incluso si era un reintento. No indica que el teléfono esté conectado ahora ni permite conocer su cola pendiente.
      </p>
      {!loading && !error && !date && <p className="mt-3 text-sm leading-6 text-muted">Si esperabas movimientos, revisa en el teléfono la vinculación, el acceso a notificaciones y la conexión con el servidor.</p>}
      <button type="button" disabled={loading} onClick={() => void refresh()} className="mt-3 text-sm font-semibold text-brand underline disabled:opacity-50">
        {error ? 'Reintentar recepción' : 'Actualizar recepción'}
      </button>
    </div>
  );
}
