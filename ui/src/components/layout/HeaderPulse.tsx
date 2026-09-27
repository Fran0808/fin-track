import React, { useEffect, useState } from 'react';
import { LogOut, AlertCircle, CheckCircle2, Smartphone } from 'lucide-react';
import { api } from '../../services';
import type { GoogleAuthStatus } from '../../types';
import { PairDeviceModal } from '../auth/PairDeviceModal';

interface HeaderPulseProps {
  error?: string | null;
}

export const HeaderPulse: React.FC<HeaderPulseProps> = ({ error }) => {
  const [googleAuth, setGoogleAuth] = useState<GoogleAuthStatus | null>(null);
  const [isConnectingGoogle, setIsConnectingGoogle] = useState(false);
  const [isDisconnecting, setIsDisconnecting] = useState(false);
  const [notification, setNotification] = useState<string | null>(null);
  const [isPairModalOpen, setIsPairModalOpen] = useState(false);

  useEffect(() => {
    // Check URL parameters for OAuth redirect notifications
    const params = new URLSearchParams(window.location.search);
    const authStatus = params.get('auth');

    if (authStatus === 'google_connected') {
      setNotification('Cuenta de Google Gmail conectada con éxito.');
      window.history.replaceState({}, '', window.location.pathname);
    } else if (authStatus === 'google_error') {
      setNotification('No se pudo completar la conexión con Google. Inténtalo nuevamente.');
      window.history.replaceState({}, '', window.location.pathname);
    }

    loadGoogleStatus();
  }, []);

  const loadGoogleStatus = async () => {
    try {
      const status = await api.getGoogleAuthStatus();
      setGoogleAuth(status);
    } catch {
      // Fallback silently if offline or endpoint not ready
    }
  };

  const handleConnectGoogle = async () => {
    try {
      setIsConnectingGoogle(true);
      const url = await api.getGoogleAuthUrl();
      window.location.href = url;
    } catch (err) {
      setIsConnectingGoogle(false);
      alert(err instanceof Error ? err.message : 'Error al iniciar conexión con Google');
    }
  };

  const handleDisconnectGoogle = async () => {
    if (!window.confirm('¿Deseas desvincular tu cuenta de Google?')) {
      return;
    }

    try {
      setIsDisconnecting(true);
      await api.disconnectGoogle();
      setGoogleAuth({ connected: false, email: null, lastSuccessfulSyncAt: null, lastSyncFailed: false });
      setNotification('Cuenta de Google desvinculada.');
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Error al desvincular');
    } finally {
      setIsDisconnecting(false);
    }
  };

  return (
    <header className="border-b border-slate-200 bg-white/90 backdrop-blur-md sticky top-0 z-30 shadow-xs">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-4">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          
          {/* Brand and Live Indicator */}
          <div className="flex items-center gap-3">
            <div className="relative flex h-3.5 w-3.5">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75" />
              <span className="relative inline-flex rounded-full h-3.5 w-3.5 bg-emerald-500 shadow-sm" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-xl font-black tracking-tight text-slate-900">
                  Wallet<span className="text-blue-600">Pulse</span>
                </h1>
              </div>
              <p className="text-xs text-slate-500 font-medium">
                Monitoreo continuo de correos bancarios y billeteras
              </p>
            </div>
          </div>

          {/* Action Bar / Google Status & Mobile Pairing */}
          <div className="flex items-center gap-2.5">
            {/* Mobile App Pairing Button */}
            <button
              type="button"
              onClick={() => setIsPairModalOpen(true)}
              className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-semibold bg-white hover:bg-slate-50 text-slate-700 border border-slate-200/90 shadow-2xs transition-all active:scale-[0.98]"
              title="Vincular aplicación móvil Android"
            >
              <Smartphone className="w-3.5 h-3.5 text-blue-600" />
              <span className="hidden sm:inline">Vincular Celular</span>
            </button>

            {googleAuth?.connected ? (
              <div className="flex items-center gap-2 bg-slate-50 border border-slate-200/90 rounded-xl px-3 py-2 text-xs text-slate-700 shadow-2xs">
                <span className="w-2 h-2 rounded-full bg-emerald-500 shadow-xs" />
                <span className="font-semibold text-slate-800 truncate max-w-[180px] sm:max-w-[240px]">
                  {googleAuth.email}
                </span>
                <button
                  type="button"
                  onClick={handleDisconnectGoogle}
                  disabled={isDisconnecting}
                  title="Desvincular cuenta de Google"
                  className="text-slate-400 hover:text-rose-600 p-0.5 rounded-md hover:bg-rose-50 transition-colors ml-1"
                >
                  <LogOut className="w-3.5 h-3.5" />
                </button>
              </div>
            ) : (
              <button
                type="button"
                onClick={handleConnectGoogle}
                disabled={isConnectingGoogle}
                className="inline-flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-semibold bg-white hover:bg-slate-50 text-slate-700 border border-slate-200/90 shadow-xs transition-all active:scale-[0.98]"
              >
                <svg className="w-3.5 h-3.5" viewBox="0 0 24 24">
                  <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
                  <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
                  <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"/>
                  <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"/>
                </svg>
                <span>{isConnectingGoogle ? 'Conectando...' : 'Conectar Gmail'}</span>
              </button>
            )}
          </div>
        </div>

        {/* Transient Notification Banner */}
        {notification && (
          <div role="status" className="mt-3 flex items-center justify-between text-xs font-medium text-emerald-900 bg-emerald-50 border border-emerald-200 px-3 py-2 rounded-xl">
            <div className="flex items-center gap-2">
              <CheckCircle2 className="w-4 h-4 text-emerald-600 flex-shrink-0" />
              <span>{notification}</span>
            </div>
            <button
              type="button"
              onClick={() => setNotification(null)}
              className="text-emerald-700 hover:text-emerald-900 font-bold text-xs ml-2"
            >
              ×
            </button>
          </div>
        )}

        {/* Transient Error Alert if any */}
        {error && (
          <div role="alert" className="mt-3 flex items-center gap-2 text-xs font-medium text-rose-800 bg-rose-50 border border-rose-200 px-3 py-2 rounded-xl">
            <AlertCircle className="w-4 h-4 text-rose-600 flex-shrink-0" />
            <span>{error}</span>
          </div>
        )}
      </div>

      {/* Mobile Device QR Pairing Modal */}
      <PairDeviceModal
        isOpen={isPairModalOpen}
        onClose={() => setIsPairModalOpen(false)}
      />
    </header>
  );
};
