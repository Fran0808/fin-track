import { useState } from 'react';
import { PhoneReceptionPanel } from '../settings/PhoneReceptionPanel';
import { emailFailureAdvice } from '../settings/sourceDiagnostics';
import {
  AlertCircle,
  CheckCircle2,
  ExternalLink,
  Mail,
  RefreshCw,
  Smartphone,
  Unplug,
} from 'lucide-react';
import { api } from '../../services/api';
import type { EmailSyncResponse, GoogleAuthStatus } from '../../types';

interface SettingsViewProps {
  googleStatus: GoogleAuthStatus | null;
  statusUnavailable: boolean;
  syncing: boolean;
  onSync: () => Promise<EmailSyncResponse>;
  onStatusRefresh: () => Promise<void>;
  onOpenPairing: () => void;
}

function formatSyncDate(value: string | null) {
  if (!value) return 'Sin registro';
  if (Number.isNaN(Date.parse(value))) return 'Fecha no disponible';
  return new Intl.DateTimeFormat('es-PE', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value));
}

export function SettingsView({
  googleStatus,
  statusUnavailable,
  syncing,
  onSync,
  onStatusRefresh,
  onOpenPairing,
}: SettingsViewProps) {
  const [connecting, setConnecting] = useState(false);
  const [disconnecting, setDisconnecting] = useState(false);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  const handleConnect = async () => {
    setConnecting(true);
    setFeedback(null);
    try {
      const url = await api.getGoogleAuthUrl();
      window.location.assign(url);
    } catch (error) {
      setConnecting(false);
      setFeedback({
        type: 'error',
        message: error instanceof Error ? error.message : 'No se pudo iniciar la conexión con Google.',
      });
    }
  };

  const handleSync = async () => {
    setFeedback(null);
    try {
      const result = await onSync();
      setFeedback({
        type: result.status === 'PARTIAL' ? 'error' : 'success',
        message: result.status === 'PARTIAL'
          ? `Sincronización incompleta: ${result.savedCount} movimientos guardados; algunos correos no pudieron procesarse. Reintenta la sincronización.`
          : result.savedCount > 0
          ? `Sincronización completa: ${result.savedCount} movimientos nuevos.`
          : 'Sincronización completa. No se encontraron movimientos nuevos.',
      });
    } catch (error) {
      setFeedback({
        type: 'error',
        message: error instanceof Error ? error.message : 'No se pudieron sincronizar los correos.',
      });
    }
  };

  const handleDisconnect = async () => {
    if (!window.confirm('¿Deseas desvincular Gmail? La sincronización de correos se detendrá.')) return;
    setDisconnecting(true);
    setFeedback(null);
    try {
      await api.disconnectGoogle();
      await onStatusRefresh();
      setFeedback({ type: 'success', message: 'Gmail fue desvinculado.' });
    } catch (error) {
      setFeedback({
        type: 'error',
        message: error instanceof Error ? error.message : 'No se pudo desvincular Gmail.',
      });
    } finally {
      setDisconnecting(false);
    }
  };

  const gmailConnected = googleStatus?.connected === true;
  const gmailFailed = !statusUnavailable && gmailConnected && googleStatus.lastSyncFailed;
  const checkingStatus = googleStatus === null && !statusUnavailable;

  return (
    <div className="space-y-6">
      <div>
        <p className="eyebrow">Captura de movimientos</p>
        <h1 className="font-display mt-2 text-3xl font-semibold tracking-tight sm:text-4xl">Configuración</h1>
        <p className="mt-2 max-w-2xl text-sm leading-6 text-muted">
          Administra las fuentes que envían movimientos a FinTrack y revisa si están listas para sincronizar.
        </p>
      </div>

      {feedback && (
        <div
          role={feedback.type === 'error' ? 'alert' : 'status'}
          className={`flex items-start gap-3 rounded-xl border px-4 py-3 text-sm ${
            feedback.type === 'error'
              ? 'border-negative/20 bg-negative/5 text-negative'
              : 'border-positive/20 bg-positive/5 text-positive'
          }`}
        >
          {feedback.type === 'error'
            ? <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
            : <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />}
          <span>{feedback.message}</span>
        </div>
      )}

      <div className="mx-auto grid w-full max-w-2xl gap-6">
        <section className="surface flex flex-col overflow-hidden" aria-labelledby="email-source-title">
          <div className="border-b border-line px-5 py-5 sm:px-6">
            <div className="flex items-start justify-between gap-4">
              <div className="flex items-start gap-3">
                <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-brand/8 text-brand">
                  <Mail className="h-5 w-5" aria-hidden="true" />
                </span>
                <div>
                  <p className="eyebrow">Fuente de movimientos</p>
                  <h2 id="email-source-title" className="font-display mt-1 text-xl font-semibold">Correos de Gmail</h2>
                </div>
              </div>
              <span className={`inline-flex items-center gap-2 rounded-full px-3 py-1 text-xs font-semibold ${
                statusUnavailable || gmailFailed
                  ? 'bg-negative/8 text-negative'
                  : gmailConnected
                    ? 'bg-positive/10 text-positive'
                    : 'bg-canvas text-muted'
              }`}>
                <span className="h-1.5 w-1.5 rounded-full bg-current" aria-hidden="true" />
                {checkingStatus ? 'Comprobando...' : statusUnavailable ? 'No disponible' : gmailFailed ? 'Requiere atención' : gmailConnected ? 'Conectado' : 'Sin conectar'}
              </span>
            </div>
          </div>

          <div className="flex flex-1 flex-col p-5 sm:p-6">
            <p className="text-sm leading-6 text-muted">
              FinTrack lee comprobantes bancarios compatibles y registra sus movimientos. No utiliza esta conexión para calcular saldos.
            </p>
            <dl className="mt-6 divide-y divide-line border-y border-line text-sm">
              <div className="flex items-center justify-between gap-4 py-3">
                <dt className="text-muted">Cuenta</dt>
                <dd className="max-w-[65%] truncate font-medium text-ink" title={googleStatus?.email ?? undefined}>
                  {googleStatus?.email ?? 'Ninguna cuenta vinculada'}
                </dd>
              </div>
              <div className="flex flex-col gap-1 py-3 sm:flex-row sm:justify-between sm:gap-4">
                <dt className="text-muted">Última sincronización correcta</dt>
                <dd className="font-medium text-ink sm:text-right">{statusUnavailable ? 'No disponible' : checkingStatus ? 'Consultando...' : formatSyncDate(googleStatus?.lastSuccessfulSyncAt ?? null)}</dd>
              </div>
              <div className="flex flex-col gap-1 py-3 sm:flex-row sm:justify-between sm:gap-4">
                <dt className="text-muted">Último intento completado</dt>
                <dd className="font-medium text-ink sm:text-right">{statusUnavailable ? 'No disponible' : checkingStatus ? 'Consultando...' : formatSyncDate(googleStatus?.lastSyncAttemptAt ?? null)}</dd>
              </div>
            </dl>

            {gmailFailed && (
              <p className="mt-4 flex gap-2 rounded-xl bg-negative/5 px-3 py-2.5 text-sm text-negative">
                <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                {emailFailureAdvice(googleStatus)}
              </p>
            )}

            <p className="mt-4 text-xs leading-5 text-muted">Este estado corresponde solo al correo. Una sincronización correcta no garantiza que todos tus gastos tengan un comprobante compatible.</p>
            <button type="button" onClick={() => void onStatusRefresh()} className="mt-3 self-start text-sm font-semibold text-brand underline">Actualizar estado de correo</button>
            <div className="mt-auto flex flex-wrap gap-3 pt-6">
              {gmailConnected ? (
                <>
                  <button
                    type="button"
                    onClick={() => void handleSync()}
                    disabled={syncing || statusUnavailable}
                    className="inline-flex items-center gap-2 rounded-xl bg-brand px-4 py-2.5 text-sm font-semibold text-white hover:bg-brand/90 disabled:cursor-wait disabled:opacity-50"
                  >
                    <RefreshCw className={`h-4 w-4 ${syncing ? 'animate-spin' : ''}`} aria-hidden="true" />
                    {syncing ? 'Sincronizando...' : 'Sincronizar ahora'}
                  </button>
                  {gmailFailed && ['AUTH_REQUIRED', 'ACCESS_DENIED'].includes(googleStatus.lastSyncFailureCode ?? '') && (
                    <button type="button" disabled={connecting} onClick={() => void handleConnect()} className="rounded-xl border border-line px-4 py-2.5 text-sm font-semibold text-brand disabled:opacity-50">{connecting ? 'Abriendo Google...' : 'Volver a conectar Gmail'}</button>
                  )}
                  <button
                    type="button"
                    onClick={() => void handleDisconnect()}
                    disabled={disconnecting}
                    className="inline-flex items-center gap-2 rounded-xl border border-line bg-white px-4 py-2.5 text-sm font-semibold text-negative hover:border-negative/30 hover:bg-negative/5 disabled:opacity-50"
                  >
                    <Unplug className="h-4 w-4" aria-hidden="true" />
                    {disconnecting ? 'Desvinculando...' : 'Desvincular Gmail'}
                  </button>
                </>
              ) : (
                <button
                  type="button"
                  onClick={() => void handleConnect()}
                  disabled={connecting || checkingStatus}
                  className="inline-flex items-center gap-2 rounded-xl bg-brand px-4 py-2.5 text-sm font-semibold text-white hover:bg-brand/90 disabled:opacity-50"
                >
                  <ExternalLink className="h-4 w-4" aria-hidden="true" />
                  {connecting ? 'Abriendo Google...' : 'Conectar Gmail'}
                </button>
              )}
            </div>
          </div>
        </section>

        <section className="surface flex flex-col overflow-hidden" aria-labelledby="mobile-source-title">
          <div className="border-b border-line px-5 py-5 sm:px-6">
            <div className="flex items-start justify-between gap-4">
              <div className="flex items-start gap-3">
                <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-yape/10 text-yape">
                  <Smartphone className="h-5 w-5" aria-hidden="true" />
                </span>
                <div>
                  <p className="eyebrow">Dispositivo móvil</p>
                  <h2 id="mobile-source-title" className="font-display mt-1 text-xl font-semibold">Teléfono</h2>
                </div>
              </div>
            </div>
          </div>

          <div className="flex flex-1 flex-col p-5 sm:p-6">
            <p className="text-sm leading-6 text-muted">
              Recibe movimientos desde las notificaciones autorizadas.
            </p>
            <PhoneReceptionPanel />
            <div className="mt-auto pt-6">
              <button
                type="button"
                onClick={onOpenPairing}
                className="inline-flex items-center gap-2 rounded-xl bg-yape px-4 py-2.5 text-sm font-semibold text-white hover:bg-yape/90"
              >
                <Smartphone className="h-4 w-4" aria-hidden="true" />
                Vincular teléfono
              </button>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
}
