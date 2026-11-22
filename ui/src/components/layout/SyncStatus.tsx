import { AlertCircle, CheckCircle2, Loader2, Mail, RefreshCw } from 'lucide-react';
import type { GoogleAuthStatus } from '../../types';
import { emailSyncState } from '../settings/sourceDiagnostics';

interface SyncStatusProps {
  status: GoogleAuthStatus | null;
  unavailable: boolean;
  syncing: boolean;
  onSync: () => void;
}

function formatSyncDate(value: string, showSeconds = false): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'Fecha no disponible';
  return new Intl.DateTimeFormat('es-PE', {
    day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit',
    second: showSeconds ? '2-digit' : undefined,
  }).format(date);
}

export function SyncStatus({ status, unavailable, syncing, onSync }: SyncStatusProps) {
  const connected = status?.connected ?? false;
  const state = emailSyncState(status);
  const failed = state === 'failed';
  const paused = state === 'paused';
  const pending = state === 'pending';
  const label = unavailable
    ? 'Estado de sincronización no disponible'
    : !status
      ? 'Consultando sincronización'
      : !connected
        ? 'Gmail no conectado'
        : paused
          ? 'Gmail en pausa'
          : pending
            ? 'Correos pendientes de sincronizar'
            : failed
              ? 'Falló la sincronización de correo'
              : status.lastSuccessfulSyncAt
                ? 'Correo sincronizado'
                : 'Aún sin sincronizar';
  const Icon = syncing ? Loader2 : failed || unavailable ? AlertCircle : pending || paused ? Mail : connected ? CheckCircle2 : Mail;
  const color = syncing ? 'text-brand' : failed || unavailable ? 'text-negative' : pending || paused ? 'text-brand' : connected ? 'text-positive' : 'text-muted';
  const lastBatch = pending && !status?.lastSyncFailed ? status?.lastSyncAttemptAt : null;

  return (
    <div className="flex items-center gap-2.5" role="status" aria-live="polite">
      <Icon aria-hidden="true" className={`h-4 w-4 shrink-0 ${color} ${syncing ? 'animate-spin' : ''}`} />
      <div className="min-w-0 max-w-64">
        <p className="text-xs font-medium text-ink">{syncing ? 'Sincronizando correos...' : label}</p>
        {!unavailable && !syncing && connected && paused && status?.syncRetryAt && (
          <p className="text-[11px] text-muted">Reintentar desde {formatSyncDate(status.syncRetryAt)}</p>
        )}
        {!unavailable && !syncing && connected && lastBatch && (
          <p className="text-[11px] text-muted">Última tanda: {formatSyncDate(lastBatch, true)}</p>
        )}
        {!unavailable && !syncing && connected && !paused && !lastBatch && status?.lastSuccessfulSyncAt && (
          <p className="text-[11px] text-muted">Última correcta: {formatSyncDate(status.lastSuccessfulSyncAt)}</p>
        )}
      </div>
      {connected && (
        <button type="button" onClick={onSync} disabled={syncing || paused} aria-label="Sincronizar correos ahora" title="Sincronizar ahora" className="rounded-lg border border-line p-2 text-brand hover:bg-canvas disabled:opacity-50">
          <RefreshCw className={`h-4 w-4 ${syncing ? 'animate-spin' : ''}`} />
        </button>
      )}
    </div>
  );
}
