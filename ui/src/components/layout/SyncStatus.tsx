import { AlertCircle, CheckCircle2, Loader2, Mail, RefreshCw } from 'lucide-react';
import type { GoogleAuthStatus } from '../../types';

interface SyncStatusProps {
  status: GoogleAuthStatus | null;
  unavailable: boolean;
  syncing: boolean;
  onSync: () => void;
}

function formatSyncDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'Fecha no disponible';
  return new Intl.DateTimeFormat('es-PE', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }).format(date);
}

export function SyncStatus({ status, unavailable, syncing, onSync }: SyncStatusProps) {
  const connected = status?.connected ?? false;
  const failed = status?.lastSyncFailed ?? false;
  const label = unavailable
    ? 'Estado de sincronización no disponible'
    : !status
      ? 'Consultando sincronización'
      : !connected
        ? 'Gmail no conectado'
        : failed
          ? 'Falló la sincronización de correo'
          : status.lastSuccessfulSyncAt
            ? `Correo sincronizado: ${formatSyncDate(status.lastSuccessfulSyncAt)}`
            : 'Aún sin sincronizar';
  const Icon = syncing ? Loader2 : failed || unavailable ? AlertCircle : connected ? CheckCircle2 : Mail;

  return (
    <div className="flex items-center gap-2.5" role="status" aria-live="polite">
      <Icon className={`h-4 w-4 shrink-0 ${syncing ? 'animate-spin text-brand' : failed || unavailable ? 'text-negative' : connected ? 'text-positive' : 'text-muted'}`} />
      <div className="min-w-0">
        <p className="max-w-48 truncate text-xs font-medium text-ink" title={label}>{syncing ? 'Sincronizando correos...' : label}</p>
        {failed && status?.lastSuccessfulSyncAt && <p className="text-[11px] text-muted">Última correcta: {formatSyncDate(status.lastSuccessfulSyncAt)}</p>}
      </div>
      {connected && (
        <button type="button" onClick={onSync} disabled={syncing} aria-label="Sincronizar correos ahora" title="Sincronizar ahora" className="rounded-lg border border-line p-2 text-brand hover:bg-canvas disabled:cursor-wait disabled:opacity-50">
          <RefreshCw className={`h-4 w-4 ${syncing ? 'animate-spin' : ''}`} />
        </button>
      )}
    </div>
  );
}
