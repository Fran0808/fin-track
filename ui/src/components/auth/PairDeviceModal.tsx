import { useEffect, useId, useRef, useState } from 'react';
import { AlertCircle, ChevronDown, Copy, Download, Loader2, RefreshCw, Smartphone, X } from 'lucide-react';
import { QRCodeSVG } from 'qrcode.react';
import { useQueryResource } from '../../hooks/useQueryResource';
import { api } from '../../services';
import type { DevicePairingInfo } from '../../types';

interface PairDeviceModalProps {
  isOpen: boolean;
  onClose: () => void;
}

const loadPairingInfo = () => api.getPairingInfo();

function getAndroidDownloadUrl() {
  try {
    const url = new URL(import.meta.env.VITE_ANDROID_APK_URL ?? '');
    return url.protocol === 'https:' && !url.username && !url.password ? url.href : null;
  } catch {
    return null;
  }
}

export function PairDeviceModal({ isOpen, onClose }: PairDeviceModalProps) {
  return isOpen ? <PairDeviceDialog onClose={onClose} /> : null;
}

function PairDeviceDialog({ onClose }: Pick<PairDeviceModalProps, 'onClose'>) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const descriptionId = useId();
  const apkUrl = getAndroidDownloadUrl();
  const { data, error, loading, refresh } = useQueryResource(
    loadPairingInfo, 'No pudimos obtener tu código de vinculación.',
  );

  useEffect(() => {
    const dialog = dialogRef.current;
    if (dialog && !dialog.open) dialog.showModal();
  }, []);

  const close = () => dialogRef.current?.close();

  return (
    <dialog
      ref={dialogRef}
      onClose={onClose}
      aria-labelledby={titleId}
      aria-describedby={descriptionId}
      className="modal-animate m-auto w-[calc(100%-1.5rem)] max-w-xl max-h-[calc(100dvh-1.5rem)] overflow-hidden rounded-2xl border border-line bg-surface p-0 text-ink shadow-2xl backdrop:bg-ink/40"
      onClick={(event) => {
        if (event.target === event.currentTarget) close();
      }}
    >
      <div className="flex max-h-[inherit] flex-col">
        <header className="flex shrink-0 items-center gap-3 border-b border-line px-4 py-4 sm:px-6">
          <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-brand/10 text-brand">
            <Smartphone size={20} aria-hidden="true" />
          </span>
          <div className="min-w-0 flex-1">
            <h2 id={titleId} className="text-lg font-semibold leading-tight">Vincular teléfono</h2>
            <p id={descriptionId} className="mt-0.5 text-sm text-muted">Conecta FinTrack en tu Android con tu cuenta.</p>
          </div>
          <button
            type="button"
            onClick={close}
            aria-label="Cerrar vinculación"
            className="grid size-10 shrink-0 place-items-center rounded-lg text-muted hover:bg-canvas hover:text-ink"
          >
            <X size={20} aria-hidden="true" />
          </button>
        </header>

        <ol className="min-h-0 space-y-5 overflow-y-auto overscroll-contain px-4 py-5 sm:px-6">
          <li className="flex items-start gap-3">
            <span aria-hidden="true" className="grid size-7 shrink-0 place-items-center rounded-full bg-brand/10 text-sm font-semibold text-brand">1</span>
            <section className="min-w-0 flex-1">
              <h3 className="text-sm font-semibold leading-7">Instala FinTrack en tu Android</h3>
              {apkUrl ? (
                <>
                  <p className="mt-0.5 text-sm leading-snug text-muted">Abre esta página en tu teléfono y descarga la app. Si ya la tienes, pasa al paso 2.</p>
                  <a
                    href={apkUrl}
                    referrerPolicy="no-referrer"
                    className="mt-3 inline-flex min-h-11 w-full items-center justify-center gap-2 rounded-xl bg-brand px-4 py-2 text-sm font-semibold text-white hover:bg-brand/90 sm:w-auto"
                  >
                    <Download size={17} aria-hidden="true" />
                    Descargar para Android
                  </a>
                </>
              ) : (
                <p className="mt-0.5 text-sm leading-snug text-muted">La descarga aún no está disponible aquí. Si ya tienes la app, pasa al paso 2.</p>
              )}
            </section>
          </li>

          <li className="flex items-start gap-3 border-t border-line pt-5">
            <span aria-hidden="true" className="grid size-7 shrink-0 place-items-center rounded-full bg-brand/10 text-sm font-semibold text-brand">2</span>
            <section className="min-w-0 flex-1">
              <h3 className="text-sm font-semibold leading-7">Escanea el código desde la app</h3>
              <p className="mt-0.5 text-sm leading-snug text-muted">Abre FinTrack en tu teléfono, toca <strong className="font-semibold text-ink">Escanear QR</strong> y apunta la cámara a este código.</p>
              {loading ? (
                <div role="status" className="mt-3 flex items-center gap-2 rounded-xl bg-canvas p-3 text-sm text-muted">
                  <Loader2 size={16} className="motion-safe:animate-spin" aria-hidden="true" />
                  Cargando tu código…
                </div>
              ) : error ? (
                <div className="mt-3 space-y-2 rounded-xl bg-negative/10 p-3">
                  <p role="alert" className="flex items-center gap-2 text-sm text-ink">
                    <AlertCircle size={16} className="shrink-0 text-negative" aria-hidden="true" />
                    No pudimos cargar tu código de vinculación.
                  </p>
                  <button type="button" onClick={() => void refresh()} className="min-h-9 rounded font-semibold text-brand underline underline-offset-2">
                    Reintentar
                  </button>
                </div>
              ) : data && <PairingCode initialInfo={data} />}
            </section>
          </li>
        </ol>
      </div>
    </dialog>
  );
}

function PairingCode({ initialInfo }: { initialInfo: DevicePairingInfo }) {
  const [pairingInfo, setPairingInfo] = useState(initialInfo);
  const [serverUrl, setServerUrl] = useState(initialInfo.serverUrl || window.location.origin);
  const [copyStatus, setCopyStatus] = useState('');
  const [regenerateError, setRegenerateError] = useState('');
  const [regenerating, setRegenerating] = useState(false);
  const serverInputRef = useRef<HTMLInputElement>(null);
  const tokenInputRef = useRef<HTMLInputElement>(null);
  const serverUrlId = useId();
  const tokenId = useId();

  async function copyField(input: HTMLInputElement | null, label: string) {
    if (!input) return;

    try {
      await navigator.clipboard.writeText(input.value.trim());
      setCopyStatus(label + ' copiado.');
    } catch {
      input.focus();
      input.select();
      setCopyStatus('El texto está seleccionado. Cópialo para pegarlo en la app.');
    }
  }

  async function regenerateCode() {
    if (!window.confirm('¿Generar un código nuevo? Tendrás que volver a vincular los teléfonos que usaban el código anterior.')) return;

    setRegenerating(true);
    setRegenerateError('');
    try {
      const newInfo = await api.regeneratePairingToken();
      setPairingInfo(newInfo);
      setServerUrl(newInfo.serverUrl || window.location.origin);
      setCopyStatus('Nuevo código listo para vincular.');
    } catch {
      setRegenerateError('No pudimos generar un código nuevo. Inténtalo otra vez.');
    } finally {
      setRegenerating(false);
    }
  }

  const qrPayload = JSON.stringify({
    token: pairingInfo.pairingToken,
    serverUrl: serverUrl.trim(),
    userEmail: pairingInfo.userEmail,
  });

  return (
    <>
      <div className="mt-3 flex flex-col items-center gap-2 rounded-xl border border-line bg-canvas p-3">
        <QRCodeSVG
          value={qrPayload}
          size={208}
          level="M"
          marginSize={4}
          title="Código QR para vincular FinTrack"
          className="h-auto w-full max-w-52 rounded-lg border border-line bg-white"
        />
        <p className="max-w-full break-all text-center text-xs text-muted">{pairingInfo.userEmail}</p>
      </div>

      <details className="group mt-3 rounded-xl border border-line">
        <summary className="flex min-h-11 list-none items-center justify-between gap-2 rounded-xl px-3 py-2 text-sm font-semibold text-brand hover:bg-canvas [&::-webkit-details-marker]:hidden">
          ¿No puedes escanear el QR?
          <ChevronDown size={17} className="shrink-0 group-open:rotate-180" aria-hidden="true" />
        </summary>
        <div className="space-y-3 border-t border-line p-3 text-sm">
          <p className="text-muted">En la app, toca <strong className="font-semibold text-ink">Configuración manual</strong> y pega estos dos datos. Puedes hacerlo desde el mismo teléfono.</p>

          <div>
            <label htmlFor={serverUrlId} className="font-medium">Dirección del servidor</label>
            <div className="mt-1 flex items-stretch gap-2">
              <input
                ref={serverInputRef}
                id={serverUrlId}
                type="url"
                value={serverUrl}
                onChange={(event) => {
                  setServerUrl(event.target.value);
                  setCopyStatus('');
                }}
                className="min-w-0 flex-1 rounded-lg border border-line bg-canvas px-2.5 py-2 text-sm"
              />
              <button type="button" aria-label="Copiar dirección del servidor" onClick={() => void copyField(serverInputRef.current, 'Enlace')} className="inline-flex min-h-10 shrink-0 items-center gap-1.5 rounded-lg border border-line px-2 text-xs font-semibold text-brand hover:bg-canvas">
                <Copy size={14} aria-hidden="true" /> Copiar
              </button>
            </div>
          </div>

          <div>
            <label htmlFor={tokenId} className="font-medium">Código de vinculación</label>
            <div className="mt-1 flex items-stretch gap-2">
              <input ref={tokenInputRef} id={tokenId} type="text" readOnly value={pairingInfo.pairingToken} className="min-w-0 flex-1 select-all rounded-lg border border-line bg-canvas px-2.5 py-2 text-sm" />
              <button type="button" aria-label="Copiar código de vinculación" onClick={() => void copyField(tokenInputRef.current, 'Código')} className="inline-flex min-h-10 shrink-0 items-center gap-1.5 rounded-lg border border-line px-2 text-xs font-semibold text-brand hover:bg-canvas">
                <Copy size={14} aria-hidden="true" /> Copiar
              </button>
            </div>
          </div>

          <p role="status" className="empty:hidden text-xs text-muted">{copyStatus}</p>
          {regenerateError && <p role="alert" className="text-xs text-negative">{regenerateError}</p>}
          <button type="button" onClick={() => void regenerateCode()} disabled={regenerating} className="inline-flex min-h-10 items-center gap-2 rounded-md text-sm font-medium text-brand hover:underline underline-offset-4 disabled:opacity-60">
            <RefreshCw size={14} aria-hidden="true" />
            {regenerating ? 'Generando código…' : 'Generar un código nuevo'}
          </button>
        </div>
      </details>
    </>
  );
}
