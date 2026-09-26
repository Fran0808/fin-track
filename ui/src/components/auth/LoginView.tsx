import { useState } from 'react';
import { ArrowRight, Loader2 } from 'lucide-react';
import { useAuth } from '../../contexts';
import { LoginWaves } from '../effects/LoginWaves';

export function LoginView() {
  const { loginWithGoogle, error } = useAuth();
  const [submitting, setSubmitting] = useState(false);

  const handleLogin = async () => {
    try {
      setSubmitting(true);
      await loginWithGoogle();
    } catch {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex min-h-screen flex-col bg-canvas text-ink">
      <header className="mx-auto flex w-full max-w-7xl items-center px-6 py-7 sm:px-10">
        <span className="mr-3 flex h-9 w-9 items-center justify-center rounded-xl bg-brand text-lg font-bold text-white" aria-hidden="true">W</span>
        <span className="font-display text-lg font-bold tracking-tight">WalletPulse</span>
      </header>

      <main className="mx-auto grid w-full max-w-7xl flex-1 items-center gap-12 px-6 py-12 sm:px-10 lg:grid-cols-[1.1fr_0.9fr] lg:gap-20">
        <div className="relative isolate max-w-2xl">
          <LoginWaves />
          <p className="eyebrow">Tu flujo de dinero, más claro</p>
          <h1 className="font-display mt-5 text-[clamp(3rem,7vw,6rem)] font-semibold leading-[1.02] tracking-tight">
            Entiende<br />cómo se mueve<br /><span className="text-brand">tu dinero.</span>
          </h1>
          <p className="mt-7 max-w-lg text-base leading-relaxed text-muted sm:text-lg">
            Reúne movimientos de Yape y correos bancarios en un solo lugar. Consulta tus gastos, entradas y medios de pago por período.
          </p>
          <div className="mt-10 flex flex-wrap gap-x-8 gap-y-3 border-t border-line pt-5 text-sm font-medium text-muted">
            <span>Movimientos confirmados</span>
            <span>Gastos por período</span>
            <span>Transferencias propias separadas</span>
          </div>
        </div>

        <section className="surface w-full max-w-md p-7 sm:p-9 lg:justify-self-end" aria-labelledby="login-heading">
          <p className="eyebrow">Acceso a WalletPulse</p>
          <h2 id="login-heading" className="font-display mt-3 text-2xl font-semibold">Entra a tu panel</h2>
          <p className="mt-2 text-sm leading-relaxed text-muted">Usa tu cuenta de Google para acceder a tus movimientos y conectar Gmail.</p>

          {error && <p role="alert" className="mt-6 rounded-xl border border-negative/20 bg-negative/5 px-4 py-3 text-sm text-negative">{error}</p>}

          <button
            type="button"
            onClick={handleLogin}
            disabled={submitting}
            className="mt-8 flex w-full items-center justify-center gap-3 rounded-xl bg-brand px-5 py-3.5 text-sm font-semibold text-white transition-colors hover:bg-[#244788] disabled:cursor-not-allowed disabled:opacity-60"
          >
            {submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <ArrowRight className="h-4 w-4" />}
            {submitting ? 'Conectando con Google...' : 'Continuar con Google'}
          </button>
          <p className="mt-5 text-xs leading-relaxed text-muted">Al continuar, autorizas la lectura de los correos necesarios para registrar tus movimientos.</p>
        </section>
      </main>

      <footer className="mx-auto w-full max-w-7xl border-t border-line px-6 py-5 text-xs text-muted sm:px-10">WalletPulse · Finanzas basadas en movimientos</footer>
    </div>
  );
}
