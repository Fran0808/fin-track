import React, { useEffect, useState, useId, useRef } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import { Smartphone, Copy, Check, RefreshCw, X, AlertTriangle, Globe } from 'lucide-react';
import { api } from '../../services';
import type { DevicePairingInfo } from '../../types';

interface PairDeviceModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const PairDeviceModal: React.FC<PairDeviceModalProps> = ({ isOpen, onClose }) => {
  const [pairingInfo, setPairingInfo] = useState<DevicePairingInfo | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [serverUrl, setServerUrl] = useState<string>('');
  const [copiedToken, setCopiedToken] = useState<boolean>(false);
  const [regenerating, setRegenerating] = useState<boolean>(false);
  const titleId = useId();
  const serverUrlId = useId();
  const dialogRef = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    if (!isOpen) return;

    let mounted = true;
    setLoading(true);
    setError(null);

    api.getPairingInfo()
      .then((info) => {
        if (!mounted) return;
        setPairingInfo(info);
        setServerUrl(info.serverUrl || window.location.origin);
      })
      .catch((err) => {
        if (!mounted) return;
        setError(err instanceof Error ? err.message : 'Error al obtener código de vinculación');
      })
      .finally(() => {
        if (mounted) setLoading(false);
      });

    return () => {
      mounted = false;
    };
  }, [isOpen]);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (isOpen && !dialog.open) dialog.showModal();
    if (!isOpen && dialog.open) dialog.close();
  }, [isOpen]);

  if (!isOpen) return null;

  const handleCopyToken = async () => {
    if (!pairingInfo?.pairingToken) return;
    try {
      await navigator.clipboard.writeText(pairingInfo.pairingToken);
      setCopiedToken(true);
      setTimeout(() => setCopiedToken(false), 2000);
    } catch {
      // Fallback
    }
  };

  const handleRegenerate = async () => {
    if (!window.confirm('¿Seguro que deseas regenerar el código? Esto desvinculará cualquier teléfono emparejado anteriormente.')) {
      return;
    }

    try {
      setRegenerating(true);
      const newInfo = await api.regeneratePairingToken();
      setPairingInfo(newInfo);
      setServerUrl(newInfo.serverUrl || window.location.origin);
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Error al regenerar código');
    } finally {
      setRegenerating(false);
    }
  };

  // Re-encode JSON payload with current customized serverUrl
  const dynamicQrPayload = pairingInfo
    ? JSON.stringify({
        token: pairingInfo.pairingToken,
        serverUrl: serverUrl.trim(),
        userEmail: pairingInfo.userEmail,
      })
    : '';

  return (
    <dialog
      ref={dialogRef}
      onClose={onClose}
      onCancel={onClose}
      aria-labelledby={titleId}
      className="m-auto w-[calc(100%-2rem)] max-w-lg rounded-2xl border-0 bg-transparent p-0 text-ink backdrop:bg-ink/40"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="bg-white rounded-2xl shadow-2xl border border-slate-200 w-full max-w-lg overflow-hidden flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="px-6 py-4 border-b border-slate-100 flex items-center justify-between bg-slate-50/50">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center border border-blue-100 shadow-2xs">
              <Smartphone className="w-5 h-5" />
            </div>
            <div>
              <h2 id={titleId} className="text-base font-bold text-slate-900 tracking-tight">
                Vincular App Móvil (Yape Listener)
              </h2>
              <p className="text-xs text-slate-500">
                Conecta tu celular para sincronizar tus finanzas automáticamente
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="text-slate-400 hover:text-slate-600 p-1.5 rounded-lg hover:bg-slate-100 transition-colors"
            title="Cerrar modal"
            aria-label="Cerrar vinculación"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content Body */}
        <div className="p-6 overflow-y-auto space-y-5">
          {loading ? (
            <div className="py-12 flex flex-col items-center justify-center gap-3 text-slate-500">
              <div className="w-8 h-8 border-3 border-blue-500 border-t-transparent rounded-full animate-spin" />
              <span className="text-xs font-medium">Cargando código de vinculación...</span>
            </div>
          ) : error ? (
            <div role="alert" className="p-4 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 text-xs flex items-center gap-2">
              <AlertTriangle className="w-4 h-4 text-rose-600 flex-shrink-0" />
              <span>{error}</span>
            </div>
          ) : pairingInfo ? (
            <>
              {/* QR Code Presentation Box */}
              <div className="flex flex-col items-center justify-center p-4 bg-slate-50 rounded-2xl border border-slate-200/80 shadow-inner">
                <div className="p-3 bg-white rounded-xl shadow-xs border border-slate-200">
                  <QRCodeSVG
                    value={dynamicQrPayload}
                    size={200}
                    level="M"
                    includeMargin={false}
                    className="w-44 h-44 sm:w-52 sm:h-52"
                  />
                </div>
                <div className="mt-3 text-center">
                  <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                    <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                    Cuenta: {pairingInfo.userEmail}
                  </span>
                </div>
              </div>

              {/* Instructions Guide */}
              <div className="bg-slate-50 border border-slate-200/90 rounded-xl p-3.5 space-y-2">
                <h4 className="text-xs font-bold text-slate-800 uppercase tracking-wider">
                  Pasos de vinculación
                </h4>
                <ol className="text-xs text-slate-600 space-y-1.5 list-decimal list-inside font-medium">
                  <li>Abre la aplicación <strong>WalletPulse</strong> en tu teléfono Android.</li>
                  <li>Toca el botón <strong>"Vincular Cuenta"</strong> en la pantalla principal.</li>
                  <li>Escanea este código QR con la cámara de la app (o ingresa el token manual).</li>
                </ol>
              </div>

              {/* Server URL Input (Editable in case WiFi IP changed) */}
              <div className="space-y-1.5">
                <label htmlFor={serverUrlId} className="text-xs font-semibold text-slate-700 flex items-center gap-1.5">
                  <Globe className="w-3.5 h-3.5 text-slate-500" />
                  <span>Dirección del Servidor (IP local / Host)</span>
                </label>
                <div className="flex items-center gap-2">
                  <input
                    id={serverUrlId}
                    type="url"
                    value={serverUrl}
                    onChange={(e) => setServerUrl(e.target.value)}
                    placeholder="http://192.168.1.5:8080"
                    className="flex-1 px-3 py-2 text-xs font-mono bg-slate-50 border border-slate-200 rounded-xl text-slate-800 focus:outline-hidden focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500"
                  />
                </div>
                <p className="text-[11px] text-slate-400">
                  Si usas una IP local, conecta el celular y la computadora a la misma red WiFi.
                  Usa la dirección del backend accesible desde el celular; localhost apunta al propio teléfono.
                </p>
              </div>

              {/* Manual Token Copy Field */}
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-700">
                  Token de Emparejamiento Manual
                </label>
                <div className="flex items-center gap-2">
                  <input
                    type="text"
                    readOnly
                    value={pairingInfo.pairingToken}
                    className="flex-1 px-3 py-2 text-xs font-mono bg-slate-100 border border-slate-200 rounded-xl text-slate-800 select-all"
                  />
                  <button
                    type="button"
                    onClick={handleCopyToken}
                    className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-semibold rounded-xl bg-white border border-slate-200 hover:bg-slate-50 text-slate-700 shadow-2xs transition-all active:scale-[0.98]"
                    title="Copiar token"
                  >
                    {copiedToken ? (
                      <>
                        <Check className="w-3.5 h-3.5 text-emerald-600" />
                        <span className="text-emerald-600">¡Copiado!</span>
                      </>
                    ) : (
                      <>
                        <Copy className="w-3.5 h-3.5 text-slate-500" />
                        <span>Copiar</span>
                      </>
                    )}
                  </button>
                </div>
              </div>
            </>
          ) : null}
        </div>

        {/* Footer */}
        <div className="px-6 py-3.5 border-t border-slate-100 bg-slate-50/70 flex items-center justify-between">
          <button
            type="button"
            onClick={handleRegenerate}
            disabled={regenerating || loading || !pairingInfo}
            className="inline-flex items-center gap-1.5 text-xs font-medium text-slate-600 hover:text-rose-600 transition-colors disabled:opacity-50"
            title="Revocar el código actual y generar uno nuevo"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${regenerating ? 'animate-spin' : ''}`} />
            <span>Regenerar Código</span>
          </button>
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 rounded-xl text-xs font-semibold bg-slate-900 hover:bg-slate-800 text-white shadow-xs transition-all active:scale-[0.98]"
          >
            Entendido
          </button>
        </div>
      </div>
    </dialog>
  );
};
